package com.trapporomeu.penlyapp.ui.editor

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.RotateLeft
import androidx.compose.material.icons.outlined.RotateRight
import com.trapporomeu.penlyapp.ui.components.ChevronLeftIcon
import com.trapporomeu.penlyapp.ui.components.ImageToolIcon
import com.trapporomeu.penlyapp.ui.components.LassoIcon
import com.trapporomeu.penlyapp.ui.components.PagesGridIcon
import com.trapporomeu.penlyapp.ui.components.PlusIcon
import com.trapporomeu.penlyapp.ui.components.ShapesIcon
import com.trapporomeu.penlyapp.ui.components.StickerIcon
import com.trapporomeu.penlyapp.ui.components.TextToolIcon
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.data.Size3
import com.trapporomeu.penlyapp.data.TextPlacement
import com.trapporomeu.penlyapp.model.ImageItem
import com.trapporomeu.penlyapp.model.TextItem
import com.trapporomeu.penlyapp.render.Exporter
import com.trapporomeu.penlyapp.render.PageRenderer
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.Screen
import com.trapporomeu.penlyapp.ui.components.EraserIcon
import com.trapporomeu.penlyapp.ui.components.HighlighterIcon
import com.trapporomeu.penlyapp.ui.components.PenIcon
import com.trapporomeu.penlyapp.ui.components.TextInputDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class EditorDialog {
    data class Text(val x: Float, val y: Float, val existing: TextItem?) : EditorDialog()
    data object Stickers : EditorDialog()
    data object Pages : EditorDialog()
    data object Template : EditorDialog()
    data object GoTo : EditorDialog()
}

@Composable
fun EditorScreen(vm: AppViewModel, notebookId: String) {
    val context = LocalContext.current
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    val cfg by vm.tools.state.collectAsStateWithLifecycle()
    val st = remember(notebookId) { EditorState(vm, notebookId) }
    val scope = rememberCoroutineScope()
    var dialog by remember { mutableStateOf<EditorDialog?>(null) }
    var menu by remember { mutableStateOf(false) }

    LaunchedEffect(notebookId) { st.load() }

    // Salva ao sair do editor e quando o app vai para segundo plano.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(st) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) st.saveNow() }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(obs)
            st.saveNow()
        }
    }

    // Barras do sistema (configuração "System bars")
    val view = LocalView.current
    DisposableEffect(settings.showSystemBars) {
        val window = (view.context as? Activity)?.window
        val ctl = window?.let { WindowCompat.getInsetsController(it, view) }
        if (!settings.showSystemBars) {
            ctl?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctl?.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { ctl?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val maxDim = when (settings.imageImportSize) { Size3.SMALL -> 900; Size3.MEDIUM -> 1500; Size3.LARGE -> 2400 }
            val res = withContext(Dispatchers.IO) { vm.repo.importImage(notebookId, uri, maxDim) } ?: return@launch
            placeImage(st, res.first, res.second, res.third, settings.imageImportSize)
        }
    }

    if (st.loadFailed) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Não foi possível abrir este caderno.")
                TextButton(onClick = { vm.back() }) { Text("Voltar") }
            }
        }
        return
    }
    val nb = st.notebook ?: return

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
        // ---------- Barra superior (mesma disposição do Penly) ----------
        // [voltar, páginas, +]  ·  [ferramentas]  ·  [páginas 1/12 ou opções da ferramenta]  ·  [cadeado, desfazer, refazer | engrenagem]
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BarButton(ChevronLeftIcon, "Voltar") { st.saveNow(); vm.back() }
            BarButton(PagesGridIcon, "Páginas") { dialog = EditorDialog.Pages }
            BarButton(PlusIcon, "Adicionar página") { st.addPageAfter() }
            Spacer(Modifier.weight(0.55f))
            ToolButton(PenIcon, "Caneta", st.tool == Tool.PEN) { st.selectTool(Tool.PEN) }
            ToolButton(HighlighterIcon, "Marca-texto", st.tool == Tool.HIGHLIGHTER) { st.selectTool(Tool.HIGHLIGHTER) }
            ToolButton(EraserIcon, "Borracha", st.tool == Tool.ERASER) { st.selectTool(Tool.ERASER) }
            ToolButton(TextToolIcon, "Texto", st.tool == Tool.TEXT) { st.selectTool(Tool.TEXT) }
            ToolButton(ShapesIcon, "Formas", st.tool == Tool.SHAPE) { st.selectTool(Tool.SHAPE) }
            ToolButton(ImageToolIcon, "Imagem", false) {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            ToolButton(StickerIcon, "Stickers", false) { dialog = EditorDialog.Stickers }
            ToolButton(LassoIcon, "Selecionar", st.tool == Tool.LASSO) { st.selectTool(Tool.LASSO) }
            val showPageNav = st.tool == Tool.NONE || (st.tool == Tool.LASSO && st.selection.isEmpty())
            if (showPageNav) {
                Spacer(Modifier.weight(1f))
                BarButton(Icons.AutoMirrored.Outlined.Undo, "Página anterior", enabled = st.pageIndex > 0) { st.goTo(st.pageIndex - 1) }
                Text(
                    "${st.pageIndex + 1} / ${nb.pages.size}",
                    Modifier.clip(MaterialTheme.shapes.small).clickable { dialog = EditorDialog.GoTo }
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                )
                BarButton(Icons.AutoMirrored.Outlined.Redo, "Próxima página", enabled = st.pageIndex < nb.pages.lastIndex) { st.goTo(st.pageIndex + 1) }
                Spacer(Modifier.weight(0.8f))
            } else {
                Spacer(Modifier.weight(1f))
                ToolbarOptions(st, cfg)
                Spacer(Modifier.width(32.dp))
            }
            if (settings.zoomLockButton) {
                BarButton(if (st.zoomLocked) Icons.Outlined.Lock else Icons.Outlined.LockOpen, "Travar zoom/rolagem") { st.zoomLocked = !st.zoomLocked }
            }
            BarButton(Icons.Outlined.RotateLeft, "Desfazer", enabled = st.canUndo) { st.undo() }
            BarButton(Icons.Outlined.RotateRight, "Refazer", enabled = st.canRedo) { st.redo() }
            VerticalDivider(Modifier.height(30.dp).padding(horizontal = 6.dp))
            Box {
                BarButton(Icons.Outlined.Settings, "Mais opções") { menu = true }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Modelo desta página") }, onClick = { menu = false; dialog = EditorDialog.Template })
                    DropdownMenuItem(text = { Text("Exportar PDF") }, onClick = {
                        menu = false
                        st.saveNow()
                        scope.launch {
                            vm.busyMessage = "Gerando PDF…"
                            try {
                                val f = withContext(Dispatchers.IO) { Exporter.exportPdf(context, nb, st.assets) }
                                Exporter.share(context, f, "application/pdf")
                            } catch (e: Exception) {
                                vm.toast = "Erro ao exportar: ${e.message}"
                            } finally {
                                vm.busyMessage = null
                            }
                        }
                    })
                    DropdownMenuItem(text = { Text("Exportar página como imagem") }, onClick = {
                        menu = false
                        scope.launch {
                            val f = withContext(Dispatchers.IO) {
                                Exporter.exportPagePng(context, nb, st.pageIndex, st.assets, settings.exportQuality.scale)
                            }
                            Exporter.share(context, f, "image/png")
                        }
                    })
                    DropdownMenuItem(text = { Text("Exportar arquivo .penlyapp (backup)") }, onClick = {
                        menu = false
                        st.saveNow()
                        scope.launch {
                            kotlinx.coroutines.delay(300)
                            val f = withContext(Dispatchers.IO) { Exporter.exportNotebookFile(context, vm.repo, nb) }
                            Exporter.share(context, f, "application/zip")
                        }
                    })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Configurações do app") }, onClick = { menu = false; vm.navigate(Screen.Settings) })
                }
            }
        }
        HorizontalDivider()
        if (st.eyedropperTarget != null) {
            Text(
                "Toque na página para pegar a cor",
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)).padding(8.dp),
                textAlign = TextAlign.Center,
            )
        }

        Box(Modifier.weight(1f)) {
            PageCanvas(
                st = st,
                settings = settings,
                onTextTap = { x, y, existing -> dialog = EditorDialog.Text(x, y, existing) },
            )
            // Botão flutuante da caneta na borda direita (como no Penly): liga/desliga a caneta.
            Box(
                Modifier.align(Alignment.CenterEnd).offset(x = 22.dp).size(70.dp).clip(CircleShape)
                    .background(Color(0xFF8E8E8E).copy(alpha = 0.85f))
                    .clickable { st.selectTool(if (st.tool == Tool.NONE) Tool.PEN else st.tool) },
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(Icons.Filled.Edit, "Caneta", tint = Color.White, modifier = Modifier.padding(start = 16.dp).size(28.dp))
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        is EditorDialog.Text -> TextInputDialog(
            title = if (d.existing != null) "Editar texto" else "Novo texto",
            initial = d.existing?.text ?: "",
            singleLine = false,
            onDismiss = { dialog = null },
        ) { text ->
            dialog = null
            val ex = d.existing
            if (ex != null) {
                st.replaceItem(ex.copy(text = text))
            } else {
                var item = TextItem(x = d.x, y = d.y, text = text, size = cfg.textSize, color = st.textColor, font = cfg.textFont, bold = cfg.textBold)
                val lay = PageRenderer.textLayout(item)
                val dx = if (settings.textPlacement == TextPlacement.MIDDLE) lay.width / 2f else 0f
                item = item.copy(x = d.x - dx, y = d.y - lay.height / 2f)
                st.addItem(item)
            }
        }
        EditorDialog.Stickers -> StickerDialog(
            repo = vm.repo,
            onDismiss = { dialog = null },
            onEmoji = { emoji ->
                dialog = null
                val p = st.page ?: return@StickerDialog
                val size = 140f
                val item = TextItem(x = p.width / 2 - size / 2, y = p.height / 2 - size / 2, text = emoji, size = size, color = 0xFF000000.toInt())
                st.addItem(item)
                st.tool = Tool.LASSO
                st.selection = setOf(item.id)
            },
            onImage = { file ->
                dialog = null
                scope.launch {
                    val res = withContext(Dispatchers.IO) { vm.repo.importStickerFile(notebookId, file) } ?: return@launch
                    placeImage(st, res.first, res.second, res.third, Size3.SMALL)
                }
            },
        )
        EditorDialog.Pages -> PagesDialog(st, onDismiss = { dialog = null })
        EditorDialog.Template -> TemplateDialog(
            onDismiss = { dialog = null },
            onPick = { t, all ->
                dialog = null
                if (all) st.setPages(st.notebook!!.pages.map { if (it.template == com.trapporomeu.penlyapp.model.PageTemplate.COVER) it else it.copy(template = t) })
                else st.setTemplate(t)
            },
        )
        EditorDialog.GoTo -> TextInputDialog("Ir para a página", "${st.pageIndex + 1}", "Ir", onDismiss = { dialog = null }) {
            dialog = null
            it.trim().toIntOrNull()?.let { n -> st.goTo(n - 1) }
        }
    }
}

private fun placeImage(st: EditorState, file: String, w: Int, h: Int, size: Size3) {
    val p = st.page ?: return
    val frac = when (size) { Size3.SMALL -> 0.3f; Size3.MEDIUM -> 0.55f; Size3.LARGE -> 0.85f }
    var iw = p.width * frac
    var ih = iw * h / w.coerceAtLeast(1)
    if (ih > p.height * 0.85f) {
        ih = p.height * 0.85f
        iw = ih * w / h.coerceAtLeast(1)
    }
    val item = ImageItem(file = file, x = (p.width - iw) / 2, y = (p.height - ih) / 2, w = iw, h = ih)
    st.addItem(item)
    st.tool = Tool.LASSO
    st.selection = setOf(item.id)
}

@Composable
private fun BarButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            icon, label,
            modifier = Modifier.size(26.dp),
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 0.8f else 0.3f),
        )
    }
}

/** Ferramenta: a escolhida fica azul (sem fundo), como no Penly. */
@Composable
private fun ToolButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.padding(horizontal = 2.dp)) {
        Icon(
            icon, label,
            modifier = Modifier.size(28.dp),
            tint = if (selected) Color(0xFF4A86E8) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}
