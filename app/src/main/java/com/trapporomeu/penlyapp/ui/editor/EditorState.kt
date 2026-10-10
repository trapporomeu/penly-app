package com.trapporomeu.penlyapp.ui.editor

import android.graphics.RectF
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewModelScope
import com.trapporomeu.penlyapp.data.ToolConfig
import com.trapporomeu.penlyapp.model.Item
import com.trapporomeu.penlyapp.model.PenType
import com.trapporomeu.penlyapp.model.Notebook
import com.trapporomeu.penlyapp.model.Page
import com.trapporomeu.penlyapp.model.PageTemplate
import com.trapporomeu.penlyapp.model.ShapeType
import com.trapporomeu.penlyapp.model.newId
import com.trapporomeu.penlyapp.render.PageRenderer
import com.trapporomeu.penlyapp.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** NONE = nenhuma ferramenta: caneta e dedo só rolam a página. */
enum class Tool { NONE, PEN, HIGHLIGHTER, ERASER, TEXT, SHAPE, LASSO }

class EditorState(val app: AppViewModel, val notebookId: String) {
    private val repo = app.repo
    private val scope = app.viewModelScope
    val assets = repo.assetsDir(notebookId)

    var notebook by mutableStateOf<Notebook?>(null)
        private set
    var loadFailed by mutableStateOf(false)
        private set
    var pageIndex by mutableIntStateOf(0)

    // Ferramenta atual. As opções de cada ferramenta ficam no ToolStore (salvas entre sessões).
    var tool by mutableStateOf(Tool.PEN)
    val tools = app.tools
    val cfg: ToolConfig get() = tools.state.value

    val penType: PenType get() = cfg.penType
    val penColor: Int get() = cfg.penColors[cfg.penColorIdx]
    val penWidth: Float get() = cfg.penWidths[cfg.penWidthIdx]
    val highlighterColor: Int get() = cfg.hlColors[cfg.hlColorIdx]
    val highlighterWidth: Float get() = cfg.hlWidths[cfg.hlWidthIdx]
    val eraserSize: Float get() = cfg.eraserSizes[cfg.eraserIdx]
    val shapeType: ShapeType get() = cfg.shapeType
    val shapeColor: Int get() = cfg.shapeColors[cfg.shapeColorIdx]
    val shapeWidth: Float get() = cfg.shapeWidths[cfg.shapeWidthIdx]
    val textColor: Int get() = cfg.textColors[cfg.textColorIdx]

    /** Toque na ferramenta já escolhida desliga ela (fica só rolando a página). */
    fun selectTool(t: Tool) {
        tool = if (tool == t) Tool.NONE else t
        if (tool != Tool.LASSO) selection = emptySet()
        eyedropperTarget = null
    }

    /** Conta-gotas: o próximo toque na página escolhe a cor para [eyedropperTarget]. */
    var eyedropperTarget by mutableStateOf<((Int) -> Unit)?>(null)

    var zoomLocked by mutableStateOf(false)
    /** Vira true quando uma caneta (stylus) é usada; aí o dedo passa a rolar/zoom (modo Automático). */
    var stylusSeen by mutableStateOf(false)
    var selection by mutableStateOf<Set<String>>(emptySet())

    private val undoStack = ArrayDeque<List<Page>>()
    private val redoStack = ArrayDeque<List<Page>>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private var liveBase: List<Page>? = null
    private var saveJob: Job? = null
    private val saveMutex = Mutex()
    private var actionsSinceBackup = 0

    val page: Page? get() = notebook?.pages?.getOrNull(pageIndex)
    val pageCount: Int get() = notebook?.pages?.size ?: 0

    suspend fun load() {
        val nb = withContext(Dispatchers.IO) { repo.loadNotebook(notebookId) }
        if (nb == null) {
            loadFailed = true
            return
        }
        notebook = nb
        pageIndex = nb.lastPage.coerceIn(0, (nb.pages.size - 1).coerceAtLeast(0))
    }

    // ---------- Histórico ----------

    private fun refreshHistoryFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    fun setPages(pages: List<Page>) {
        val nb = notebook ?: return
        undoStack.addLast(nb.pages)
        if (undoStack.size > 100) undoStack.removeFirst()
        redoStack.clear()
        notebook = nb.copy(pages = pages)
        pageIndex = pageIndex.coerceIn(0, pages.lastIndex)
        refreshHistoryFlags()
        changed()
    }

    fun updatePage(transform: (Page) -> Page) {
        val nb = notebook ?: return
        val idx = pageIndex
        setPages(nb.pages.mapIndexed { i, p -> if (i == idx) transform(p) else p })
    }

    /** Mudanças contínuas (ex.: borracha) entram no histórico como uma ação só. */
    fun beginLive() {
        liveBase = notebook?.pages
    }

    fun liveUpdatePage(transform: (Page) -> Page) {
        val nb = notebook ?: return
        val idx = pageIndex
        notebook = nb.copy(pages = nb.pages.mapIndexed { i, p -> if (i == idx) transform(p) else p })
    }

    fun endLive() {
        val base = liveBase ?: return
        liveBase = null
        val nb = notebook ?: return
        if (base !== nb.pages) {
            undoStack.addLast(base)
            if (undoStack.size > 100) undoStack.removeFirst()
            redoStack.clear()
            refreshHistoryFlags()
            changed()
        }
    }

    fun undo() {
        val nb = notebook ?: return
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(nb.pages)
        notebook = nb.copy(pages = prev)
        pageIndex = pageIndex.coerceIn(0, prev.lastIndex)
        selection = emptySet()
        refreshHistoryFlags()
        changed()
    }

    fun redo() {
        val nb = notebook ?: return
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(nb.pages)
        notebook = nb.copy(pages = next)
        pageIndex = pageIndex.coerceIn(0, next.lastIndex)
        selection = emptySet()
        refreshHistoryFlags()
        changed()
    }

    // ---------- Salvar / backup ----------

    private fun changed() {
        actionsSinceBackup++
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(1200)
            saveNow()
        }
    }

    fun saveNow() {
        val nb = notebook ?: return
        val idx = pageIndex
        saveJob?.cancel()
        val doBackup = app.settings.value.autoBackups && actionsSinceBackup >= app.settings.value.actionsPerBackup
        if (doBackup) actionsSinceBackup = 0
        scope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                repo.saveNotebook(nb.copy(lastPage = idx))
                if (doBackup) repo.backup(notebookId)
            }
        }
    }

    // ---------- Páginas ----------

    fun goTo(index: Int) {
        val n = pageCount
        if (n == 0) return
        val i = index.coerceIn(0, n - 1)
        if (i != pageIndex) {
            pageIndex = i
            selection = emptySet()
        }
    }

    fun addPageAfter(template: PageTemplate? = null) {
        val nb = notebook ?: return
        val cur = page ?: return
        val base = if (cur.template == PageTemplate.COVER || cur.background != null) Page(width = cur.width, height = cur.height, template = PageTemplate.LINED)
        else Page(width = cur.width, height = cur.height, template = cur.template, bgColor = cur.bgColor)
        val newPage = if (template != null) base.copy(template = template) else base
        val pages = nb.pages.toMutableList().apply { add(pageIndex + 1, newPage) }
        setPages(pages)
        goTo(pageIndex + 1)
    }

    fun duplicatePage(index: Int) {
        val nb = notebook ?: return
        val p = nb.pages.getOrNull(index) ?: return
        val copy = p.copy(id = newId(), items = p.items.map { reId(it) })
        setPages(nb.pages.toMutableList().apply { add(index + 1, copy) })
    }

    fun deletePage(index: Int) {
        val nb = notebook ?: return
        if (nb.pages.size <= 1) return
        setPages(nb.pages.toMutableList().apply { removeAt(index) })
        if (pageIndex >= index && pageIndex > 0) pageIndex = (pageIndex - 1).coerceAtMost(pageCount - 1)
    }

    fun movePage(from: Int, to: Int) {
        val nb = notebook ?: return
        if (to !in nb.pages.indices || from !in nb.pages.indices) return
        val list = nb.pages.toMutableList()
        val p = list.removeAt(from)
        list.add(to, p)
        setPages(list)
        if (pageIndex == from) pageIndex = to
    }

    fun setTemplate(template: PageTemplate) = updatePage { it.copy(template = template) }

    // ---------- Itens ----------

    private fun reId(item: Item): Item = when (item) {
        is com.trapporomeu.penlyapp.model.StrokeItem -> item.copy(id = newId())
        is com.trapporomeu.penlyapp.model.TextItem -> item.copy(id = newId())
        is com.trapporomeu.penlyapp.model.ShapeItem -> item.copy(id = newId())
        is com.trapporomeu.penlyapp.model.ImageItem -> item.copy(id = newId())
    }

    fun addItem(item: Item) = updatePage { it.copy(items = it.items + item) }

    fun replaceItem(item: Item) = updatePage { p -> p.copy(items = p.items.map { if (it.id == item.id) item else it }) }

    fun deleteSelection() {
        val sel = selection
        if (sel.isEmpty()) return
        updatePage { p -> p.copy(items = p.items.filterNot { it.id in sel }) }
        selection = emptySet()
    }

    fun duplicateSelection() {
        val p = page ?: return
        val copies = p.items.filter { it.id in selection }.map { PageRenderer.translate(reId(it), 30f, 30f) }
        if (copies.isEmpty()) return
        updatePage { it.copy(items = it.items + copies) }
        selection = copies.map { it.id }.toSet()
    }

    fun recolorSelection(color: Int) {
        val sel = selection
        updatePage { p ->
            p.copy(items = p.items.map {
                if (it.id !in sel) it else when (it) {
                    is com.trapporomeu.penlyapp.model.StrokeItem -> it.copy(color = color)
                    is com.trapporomeu.penlyapp.model.TextItem -> it.copy(color = color)
                    is com.trapporomeu.penlyapp.model.ShapeItem -> it.copy(color = color)
                    else -> it
                }
            })
        }
    }

    fun transformSelection(dx: Float, dy: Float, scale: Float, ox: Float, oy: Float) {
        val sel = selection
        updatePage { p ->
            p.copy(items = p.items.map {
                if (it.id !in sel) it
                else {
                    val scaled = if (scale != 1f) PageRenderer.scale(it, scale, ox, oy) else it
                    PageRenderer.translate(scaled, dx, dy)
                }
            })
        }
    }

    fun selectionBounds(): RectF? {
        val p = page ?: return null
        val items = p.items.filter { it.id in selection }
        if (items.isEmpty()) return null
        val r = RectF(PageRenderer.bounds(items[0]))
        items.drop(1).forEach { r.union(PageRenderer.bounds(it)) }
        return r
    }
}
