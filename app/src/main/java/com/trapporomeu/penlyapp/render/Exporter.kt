package com.trapporomeu.penlyapp.render

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.trapporomeu.penlyapp.data.NotebookRepository
import com.trapporomeu.penlyapp.model.Notebook
import java.io.File
import java.io.FileOutputStream

object Exporter {
    private fun exportsDir(context: Context) = File(context.cacheDir, "exports").apply { mkdirs() }

    private fun safeName(name: String) = name.replace(Regex("[^\\p{L}\\p{N} _-]"), "_").ifBlank { "caderno" }

    /** PDF com todas as páginas (vetorial: traços continuam nítidos com zoom). */
    fun exportPdf(context: Context, nb: Notebook, assets: File): File {
        val doc = PdfDocument()
        // 1000 unidades de página = 595 pt (largura de uma folha A4)
        val k = 595f / 1000f
        nb.pages.forEachIndexed { i, page ->
            val info = PdfDocument.PageInfo.Builder((page.width * k).toInt(), (page.height * k).toInt(), i + 1).create()
            val p = doc.startPage(info)
            p.canvas.scale(k, k)
            PageRenderer.drawPage(p.canvas, page, assets)
            doc.finishPage(p)
        }
        val out = File(exportsDir(context), "${safeName(nb.name)}.pdf")
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
        return out
    }

    fun exportPagePng(context: Context, nb: Notebook, pageIndex: Int, assets: File, quality: Float): File {
        val page = nb.pages[pageIndex]
        val bmp = PageRenderer.renderBitmap(context, page, assets, quality)
        val out = File(exportsDir(context), "${safeName(nb.name)}_p${pageIndex + 1}.png")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return out
    }

    fun exportNotebookFile(context: Context, repo: NotebookRepository, nb: Notebook): File {
        val out = File(exportsDir(context), "${safeName(nb.name)}.penlyapp")
        FileOutputStream(out).use { repo.exportNotebookFile(nb.id, it) }
        return out
    }

    fun share(context: Context, file: File, mime: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Compartilhar").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
