package com.trapporomeu.penlyapp.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import com.trapporomeu.penlyapp.model.BackupInfo
import com.trapporomeu.penlyapp.model.Folder
import com.trapporomeu.penlyapp.model.Library
import com.trapporomeu.penlyapp.model.Notebook
import com.trapporomeu.penlyapp.model.NotebookMeta
import com.trapporomeu.penlyapp.model.PAGE_H
import com.trapporomeu.penlyapp.model.PAGE_W
import com.trapporomeu.penlyapp.model.Page
import com.trapporomeu.penlyapp.model.PageTemplate
import com.trapporomeu.penlyapp.model.newId
import com.trapporomeu.penlyapp.render.BitmapCache
import com.trapporomeu.penlyapp.render.PageRenderer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

val AppJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    classDiscriminator = "type"
}

class NotebookRepository(private val context: Context, private val settings: SettingsStore) {
    private val root = context.filesDir
    private val libraryFile = File(root, "library.json")
    private val notebooksDir = File(root, "notebooks").apply { mkdirs() }
    private val backupsDir = File(root, "backups").apply { mkdirs() }
    val fontsDir = File(root, "fonts").apply { mkdirs() }
    val stickersDir = File(root, "stickers").apply { mkdirs() }

    private val lock = Any()
    private val _library = MutableStateFlow(loadLibrary())
    val library: StateFlow<Library> = _library.asStateFlow()

    // ---------- Biblioteca ----------

    private fun loadLibrary(): Library = try {
        if (libraryFile.exists()) AppJson.decodeFromString<Library>(libraryFile.readText()) else Library()
    } catch (e: Exception) {
        Library()
    }

    private fun updateLibrary(block: (Library) -> Library) = synchronized(lock) {
        val new = block(_library.value)
        _library.value = new
        writeAtomic(libraryFile, AppJson.encodeToString(new))
    }

    private fun writeAtomic(file: File, text: String) {
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    fun notebookDir(id: String) = File(notebooksDir, id)
    fun assetsDir(id: String) = File(notebookDir(id), "assets").apply { mkdirs() }
    fun thumbFile(id: String) = File(notebookDir(id), "thumb.png")
    private fun notebookFile(id: String) = File(notebookDir(id), "notebook.json")

    fun meta(id: String): NotebookMeta? = _library.value.notebooks.firstOrNull { it.id == id }

    // ---------- Cadernos ----------

    fun loadNotebook(id: String): Notebook? = try {
        AppJson.decodeFromString<Notebook>(notebookFile(id).readText())
    } catch (e: Exception) {
        null
    }

    fun saveNotebook(nb: Notebook) {
        notebookDir(nb.id).mkdirs()
        writeAtomic(notebookFile(nb.id), AppJson.encodeToString(nb))
        renderThumbnail(nb)
        val now = System.currentTimeMillis()
        updateLibrary { lib ->
            lib.copy(notebooks = lib.notebooks.map {
                if (it.id == nb.id) it.copy(name = nb.name, updatedAt = now, pageCount = nb.pages.size) else it
            })
        }
    }

    private fun renderThumbnail(nb: Notebook) {
        val idx = if (settings.value.alwaysShowFirstPage) 0 else nb.lastPage.coerceIn(0, nb.pages.lastIndex)
        val page = nb.pages.getOrNull(idx) ?: return
        try {
            val bmp = PageRenderer.renderBitmap(context, page, assetsDir(nb.id), 360f / page.width)
            FileOutputStream(thumbFile(nb.id)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bmp.recycle()
        } catch (_: Throwable) {
        }
    }

    fun createNotebook(name: String, folderId: String?, pages: List<Page>, id: String = newId()): Notebook {
        val nb = Notebook(id = id, name = name, pages = pages)
        val now = System.currentTimeMillis()
        updateLibrary { lib ->
            lib.copy(notebooks = lib.notebooks + NotebookMeta(id, name, folderId, now, now, null, pages.size))
        }
        saveNotebook(nb)
        return nb
    }

    fun renameNotebook(id: String, name: String) {
        loadNotebook(id)?.let { saveNotebook(it.copy(name = name)) }
    }

    fun moveNotebook(id: String, folderId: String?) = updateLibrary { lib ->
        lib.copy(notebooks = lib.notebooks.map { if (it.id == id) it.copy(folderId = folderId) else it })
    }

    fun duplicateNotebook(id: String) {
        val src = loadNotebook(id) ?: return
        val m = meta(id) ?: return
        val newId = newId()
        assetsDir(id).copyRecursively(assetsDir(newId), overwrite = true)
        createNotebook("${src.name} (cópia)", m.folderId, src.pages, newId)
    }

    fun trashNotebook(id: String) = updateLibrary { lib ->
        lib.copy(notebooks = lib.notebooks.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it })
    }

    fun restoreNotebook(id: String) = updateLibrary { lib ->
        val folderIds = lib.folders.filter { it.deletedAt == null }.map { it.id }.toSet()
        lib.copy(notebooks = lib.notebooks.map {
            if (it.id == id) it.copy(deletedAt = null, folderId = it.folderId?.takeIf { f -> f in folderIds }) else it
        })
    }

    fun deleteNotebookForever(id: String) {
        notebookDir(id).deleteRecursively()
        File(backupsDir, id).deleteRecursively()
        updateLibrary { lib -> lib.copy(notebooks = lib.notebooks.filterNot { it.id == id }) }
    }

    // ---------- Pastas ----------

    fun createFolder(name: String, parentId: String?) {
        val now = System.currentTimeMillis()
        updateLibrary { it.copy(folders = it.folders + Folder(name = name, parentId = parentId, createdAt = now, updatedAt = now)) }
    }

    fun renameFolder(id: String, name: String) = updateLibrary { lib ->
        lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(name = name, updatedAt = System.currentTimeMillis()) else it })
    }

    fun moveFolder(id: String, parentId: String?) = updateLibrary { lib ->
        // Evita mover uma pasta para dentro dela mesma.
        if (parentId != null && (parentId == id || isDescendant(lib, parentId, id))) lib
        else lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(parentId = parentId) else it })
    }

    private fun isDescendant(lib: Library, folderId: String, ancestorId: String): Boolean {
        var cur = lib.folders.firstOrNull { it.id == folderId }?.parentId
        while (cur != null) {
            if (cur == ancestorId) return true
            cur = lib.folders.firstOrNull { it.id == cur }?.parentId
        }
        return false
    }

    fun trashFolder(id: String) = updateLibrary { lib ->
        lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(deletedAt = System.currentTimeMillis()) else it })
    }

    fun restoreFolder(id: String) = updateLibrary { lib ->
        lib.copy(folders = lib.folders.map { if (it.id == id) it.copy(deletedAt = null) else it })
    }

    fun deleteFolderForever(id: String) {
        val lib = _library.value
        lib.notebooks.filter { it.folderId == id }.forEach { deleteNotebookForever(it.id) }
        lib.folders.filter { it.parentId == id }.forEach { deleteFolderForever(it.id) }
        updateLibrary { l -> l.copy(folders = l.folders.filterNot { it.id == id }) }
    }

    fun emptyTrash() {
        val lib = _library.value
        lib.notebooks.filter { it.deletedAt != null }.forEach { deleteNotebookForever(it.id) }
        lib.folders.filter { it.deletedAt != null }.forEach { deleteFolderForever(it.id) }
    }

    // ---------- Backups automáticos ----------

    fun backup(id: String) {
        val src = notebookFile(id)
        if (!src.exists()) return
        val dir = File(backupsDir, id).apply { mkdirs() }
        src.copyTo(File(dir, "${System.currentTimeMillis()}.json"), overwrite = true)
        val max = settings.value.maxBackups
        dir.listFiles()?.sortedByDescending { it.name }?.drop(max)?.forEach { it.delete() }
    }

    fun listBackups(): List<BackupInfo> =
        backupsDir.listFiles()?.flatMap { dir ->
            dir.listFiles()?.mapNotNull { f ->
                f.nameWithoutExtension.toLongOrNull()?.let { BackupInfo(dir.name, f.name, it) }
            } ?: emptyList()
        }?.filter { meta(it.notebookId) != null }?.sortedByDescending { it.time } ?: emptyList()

    fun restoreBackup(info: BackupInfo) {
        val f = File(File(backupsDir, info.notebookId), info.file)
        val nb = try {
            AppJson.decodeFromString<Notebook>(f.readText())
        } catch (e: Exception) {
            return
        }
        backup(info.notebookId) // guarda o estado atual antes de restaurar
        saveNotebook(nb)
        restoreNotebook(info.notebookId)
    }

    // ---------- Imagens ----------

    /** Copia uma imagem para os assets do caderno. Retorna (arquivo, largura, altura). */
    fun importImage(notebookId: String, uri: Uri, maxDim: Int): Triple<String, Int, Int>? {
        val bmp = decodeScaled(uri, maxDim) ?: return null
        val name = "img_${newId()}.png"
        FileOutputStream(File(assetsDir(notebookId), name)).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val r = Triple(name, bmp.width, bmp.height)
        bmp.recycle()
        return r
    }

    fun importStickerFile(notebookId: String, sticker: File): Triple<String, Int, Int>? {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(sticker.absolutePath, opts)
        if (opts.outWidth <= 0) return null
        val name = "img_${newId()}.${sticker.extension.ifEmpty { "png" }}"
        sticker.copyTo(File(assetsDir(notebookId), name), overwrite = true)
        return Triple(name, opts.outWidth, opts.outHeight)
    }

    fun addSticker(uri: Uri) {
        val bmp = decodeScaled(uri, 600) ?: return
        FileOutputStream(File(stickersDir, "st_${System.currentTimeMillis()}.png")).use {
            bmp.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bmp.recycle()
    }

    fun addFont(uri: Uri, displayName: String?): Boolean {
        val name = (displayName ?: "fonte_${System.currentTimeMillis()}.ttf").replace("/", "_")
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext != "ttf" && ext != "otf") return false
        val dest = File(fontsDir, name)
        context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
        return try {
            android.graphics.Typeface.createFromFile(dest)
            true
        } catch (e: Exception) {
            dest.delete()
            false
        }
    }

    private fun decodeScaled(uri: Uri, maxDim: Int): Bitmap? {
        val cr = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxDim || bounds.outHeight / (sample * 2) >= maxDim) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val scale = minOf(1f, maxDim.toFloat() / maxOf(bmp.width, bmp.height))
        if (scale >= 1f) return bmp
        val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
        if (scaled != bmp) bmp.recycle()
        return scaled
    }

    // ---------- Importar PDF ----------

    fun importPdf(uri: Uri, name: String, folderId: String?, onProgress: (Int, Int) -> Unit): Notebook? {
        val id = newId()
        val assets = assetsDir(id)
        val pages = mutableListOf<Page>()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
        pfd.use {
            PdfRenderer(it).use { renderer ->
                for (i in 0 until renderer.pageCount) {
                    onProgress(i + 1, renderer.pageCount)
                    renderer.openPage(i).use { p ->
                        val targetW = 1600
                        val scale = targetW.toFloat() / p.width
                        val w = targetW
                        val h = (p.height * scale).toInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        val file = "pdf_${i + 1}.jpg"
                        FileOutputStream(File(assets, file)).use { out -> bmp.compress(Bitmap.CompressFormat.JPEG, 92, out) }
                        bmp.recycle()
                        pages += Page(width = PAGE_W, height = PAGE_W * p.height / p.width, background = file)
                    }
                }
            }
        }
        if (pages.isEmpty()) return null
        return createNotebook(name, folderId, pages, id)
    }

    // ---------- Exportar / importar arquivo .penlyapp (zip) ----------

    fun exportNotebookFile(id: String, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry("notebook.json"))
            notebookFile(id).inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
            assetsDir(id).listFiles()?.forEach { f ->
                zip.putNextEntry(ZipEntry("assets/${f.name}"))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    fun importNotebookFile(input: InputStream, folderId: String?): Notebook? {
        val id = newId()
        val assets = assetsDir(id)
        var json: String? = null
        ZipInputStream(input).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                val n = e.name
                when {
                    n == "notebook.json" -> json = zip.readBytes().toString(Charsets.UTF_8)
                    n.startsWith("assets/") && !n.contains("..") && !e.isDirectory ->
                        File(assets, n.removePrefix("assets/")).outputStream().use { zip.copyTo(it) }
                }
                e = zip.nextEntry
            }
        }
        val nb = try {
            AppJson.decodeFromString<Notebook>(json ?: return null)
        } catch (e: Exception) {
            notebookDir(id).deleteRecursively()
            return null
        }
        return createNotebook(nb.name, folderId, nb.pages, id)
    }

    // ---------- Planners iniciais ----------

    fun createStarterPlanners() {
        val blue = 0xFF7FA7D9.toInt()
        createNotebook(
            "Caderno Retrato", null,
            listOf(Page(template = PageTemplate.COVER, bgColor = blue, title = "Caderno")) +
                List(20) { Page(template = PageTemplate.LINED) },
        )
        createNotebook(
            "Caderno Paisagem", null,
            List(20) { Page(width = PAGE_H, height = PAGE_W, template = PageTemplate.GRID) },
        )
        createNotebook("Lista de Tarefas", null, List(10) { Page(template = PageTemplate.TODO) })
        createNotebook(
            "Planner Semanal", null,
            listOf(Page(template = PageTemplate.COVER, bgColor = 0xFFA9D09A.toInt(), title = "Planner")) +
                List(12) { Page(width = PAGE_H, height = PAGE_W, template = PageTemplate.WEEKLY) },
        )
        createNotebook("Pontilhado", null, List(20) { Page(template = PageTemplate.DOTS) })
    }

    fun clearCaches() = BitmapCache.clear()
}
