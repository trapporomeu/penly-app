package com.trapporomeu.penlyapp.ui.editor

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CropSquare
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.HorizontalRule
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.trapporomeu.penlyapp.data.ToolConfig
import com.trapporomeu.penlyapp.data.replaced
import com.trapporomeu.penlyapp.model.PenType
import com.trapporomeu.penlyapp.model.ShapeItem
import com.trapporomeu.penlyapp.model.ShapeType
import com.trapporomeu.penlyapp.model.StrokeItem
import com.trapporomeu.penlyapp.render.FontRegistry
import com.trapporomeu.penlyapp.ui.components.BrushIcon
import com.trapporomeu.penlyapp.ui.components.FountainPenIcon
import com.trapporomeu.penlyapp.ui.components.PenIcon
import com.trapporomeu.penlyapp.ui.components.SmoothingIcon
import com.trapporomeu.penlyapp.ui.components.displayName
import kotlin.math.roundToInt

fun penIcon(t: PenType): ImageVector = when (t) {
    PenType.PENCIL -> PenIcon
    PenType.FOUNTAIN -> FountainPenIcon
    PenType.BRUSH -> BrushIcon
}

/** Posiciona a janelinha logo abaixo do botão, sem sair da tela. */
private class BelowAnchor(private val gap: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = anchorBounds.left.coerceIn(0, maxOf(0, windowSize.width - popupContentSize.width))
        val y = (anchorBounds.bottom + gap).coerceIn(0, maxOf(0, windowSize.height - popupContentSize.height))
        return IntOffset(x, y)
    }
}

/** Janelinha que abre logo abaixo do botão da barra (como no Penly). */
@Composable
fun ToolbarPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { BelowAnchor(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Surface(shape = RoundedCornerShape(10.dp), shadowElevation = 8.dp, tonalElevation = 2.dp) { content() }
    }
}

@Composable
private fun BarIcon(icon: ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            icon, label,
            modifier = Modifier.size(26.dp),
            tint = if (selected) Color(0xFF4A86E8) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun Divider() = VerticalDivider(Modifier.height(28.dp).padding(horizontal = 8.dp))

@Composable
private fun PenTypeButton(current: PenType, onPick: (PenType) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIcon(penIcon(current), "Tipo de caneta") { open = true }
        if (open) ToolbarPopup({ open = false }) {
            Column(Modifier.padding(vertical = 6.dp)) {
                listOf(PenType.PENCIL to "Lápis", PenType.FOUNTAIN to "Caneta-tinteiro", PenType.BRUSH to "Pincel").forEach { (t, label) ->
                    BarIcon(penIcon(t), label) { onPick(t); open = false }
                }
            }
        }
    }
}

@Composable
private fun SmoothingButton(value: Int, onChange: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        BarIcon(SmoothingIcon, "Suavização") { open = true }
        if (open) ToolbarPopup({ open = false }) {
            Column(Modifier.width(230.dp).padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Suavização:", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.width(60.dp)) {
                        Text("$value")
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
                Slider(value.toFloat(), { onChange(it.roundToInt()) }, valueRange = 0f..100f)
                TextButton(onClick = { open = false }, modifier = Modifier.align(Alignment.End)) { Text("PRONTO", color = Color(0xFF4A86E8)) }
            }
        }
    }
}

/** Três espessuras. Toque numa para usar; toque de novo na escolhida para ajustar. */
@Composable
private fun WidthSlots(widths: List<Float>, idx: Int, range: ClosedFloatingPointRange<Float>, onSelect: (Int) -> Unit, onEdit: (Int, Float) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    widths.forEachIndexed { i, w ->
        Box {
            val selected = i == idx
            Box(
                Modifier.size(width = 52.dp, height = 44.dp).clip(RoundedCornerShape(8.dp))
                    .clickable { if (selected) editing = i else onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(width = 34.dp, height = (2f + 7f * (i.toFloat() / (widths.size - 1).coerceAtLeast(1))).dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color(0xFF8FB3EE) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)),
                )
            }
            if (editing == i) ToolbarPopup({ editing = null }) {
                Column(Modifier.width(240.dp).padding(16.dp)) {
                    Text("Espessura: ${"%.1f".format(w)}", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    Slider(w, { onEdit(i, (it * 2).roundToInt() / 2f) }, valueRange = range)
                    TextButton(onClick = { editing = null }, modifier = Modifier.align(Alignment.End)) { Text("PRONTO", color = Color(0xFF4A86E8)) }
                }
            }
        }
    }
}

/** Quatro cores. Toque numa para usar; toque de novo na escolhida para abrir o seletor de cores. */
@Composable
private fun ColorSlots(st: EditorState, cfg: ToolConfig, colors: List<Int>, idx: Int, onSelect: (Int) -> Unit, onEdit: (Int, Int) -> Unit) {
    var editing by remember { mutableStateOf<Int?>(null) }
    colors.forEachIndexed { i, c ->
        Box {
            val selected = i == idx
            Box(
                Modifier.size(48.dp).clickable { if (selected) editing = i else onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier.size(30.dp)
                        .border(if (selected) 3.dp else 0.dp, if (selected) Color(0xFFB9D0F5) else Color.Transparent, CircleShape)
                        .padding(if (selected) 3.dp else 0.dp)
                        .clip(CircleShape).background(Color(c)),
                )
            }
            if (editing == i) ToolbarPopup({ editing = null }) {
                ColorPicker(
                    color = c,
                    palette = cfg.palette,
                    onColor = { onEdit(i, it) },
                    onPaletteChange = { p -> st.tools.update { it.copy(palette = p) } },
                    onEyedropper = {
                        editing = null
                        st.eyedropperTarget = { picked -> onEdit(i, picked) }
                    },
                )
            }
        }
    }
}

/** Opções da ferramenta atual, mostradas na própria barra superior. */
@Composable
fun ToolbarOptions(st: EditorState, cfg: ToolConfig) {
    val tools = st.tools
    when (st.tool) {
        Tool.PEN -> {
            PenTypeButton(cfg.penType) { t -> tools.update { it.copy(penType = t) } }
            SmoothingButton(cfg.smoothing) { v -> tools.update { it.copy(smoothing = v) } }
            Divider()
            WidthSlots(cfg.penWidths, cfg.penWidthIdx, 0.5f..30f,
                onSelect = { i -> tools.update { it.copy(penWidthIdx = i) } },
                onEdit = { i, w -> tools.update { it.copy(penWidths = it.penWidths.replaced(i, w)) } })
            Divider()
            ColorSlots(st, cfg, cfg.penColors, cfg.penColorIdx,
                onSelect = { i -> tools.update { it.copy(penColorIdx = i) } },
                onEdit = { i, c -> tools.update { it.copy(penColors = it.penColors.replaced(i, c)) } })
        }
        Tool.HIGHLIGHTER -> {
            WidthSlots(cfg.hlWidths, cfg.hlWidthIdx, 6f..80f,
                onSelect = { i -> tools.update { it.copy(hlWidthIdx = i) } },
                onEdit = { i, w -> tools.update { it.copy(hlWidths = it.hlWidths.replaced(i, w)) } })
            Divider()
            ColorSlots(st, cfg, cfg.hlColors, cfg.hlColorIdx,
                onSelect = { i -> tools.update { it.copy(hlColorIdx = i) } },
                onEdit = { i, c -> tools.update { it.copy(hlColors = it.hlColors.replaced(i, c)) } })
        }
        Tool.ERASER -> {
            WidthSlots(cfg.eraserSizes, cfg.eraserIdx, 3f..120f,
                onSelect = { i -> tools.update { it.copy(eraserIdx = i) } },
                onEdit = { i, w -> tools.update { it.copy(eraserSizes = it.eraserSizes.replaced(i, w)) } })
            Divider()
            BarIcon(Icons.Outlined.DeleteSweep, "Apagar toda a tinta da página") {
                st.updatePage { p -> p.copy(items = p.items.filterNot { it is StrokeItem || it is ShapeItem }) }
            }
        }
        Tool.TEXT -> {
            var fontMenu by remember { mutableStateOf(false) }
            var fontsVersion by remember { mutableStateOf(0) }
            val context = LocalContext.current
            val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    val ok = st.app.repo.addFont(uri, displayName(context, uri))
                    st.app.toast = if (ok) "Fonte adicionada" else "Arquivo de fonte inválido (use .ttf ou .otf)"
                    fontsVersion++
                }
            }
            Box {
                TextButton(onClick = { fontMenu = true }) { Text(cfg.textFont ?: "Padrão") }
                DropdownMenu(fontMenu, { fontMenu = false }) {
                    remember(fontsVersion) { FontRegistry.allNames() }.forEach { name ->
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            tools.update { it.copy(textFont = if (name == "Padrão") null else name) }
                            fontMenu = false
                        })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Adicionar fonte (.ttf/.otf)…") }, onClick = {
                        fontMenu = false
                        fontPicker.launch(arrayOf("*/*"))
                    })
                }
            }
            IconButton(onClick = { tools.update { it.copy(textSize = (it.textSize - 4f).coerceAtLeast(12f)) } }) { Icon(Icons.Outlined.Remove, "Menor") }
            Text("${cfg.textSize.toInt()}", fontWeight = FontWeight.Bold)
            IconButton(onClick = { tools.update { it.copy(textSize = (it.textSize + 4f).coerceAtMost(200f)) } }) { Icon(Icons.Default.Add, "Maior") }
            BarIcon(Icons.Outlined.FormatBold, "Negrito", cfg.textBold) { tools.update { it.copy(textBold = !it.textBold) } }
            Divider()
            ColorSlots(st, cfg, cfg.textColors, cfg.textColorIdx,
                onSelect = { i -> tools.update { it.copy(textColorIdx = i) } },
                onEdit = { i, c -> tools.update { it.copy(textColors = it.textColors.replaced(i, c)) } })
        }
        Tool.SHAPE -> {
            listOf(
                Triple(ShapeType.RECT, Icons.Outlined.CropSquare, "Retângulo"),
                Triple(ShapeType.OVAL, Icons.Outlined.Circle, "Oval"),
                Triple(ShapeType.LINE, Icons.Outlined.HorizontalRule, "Linha"),
                Triple(ShapeType.ARROW, Icons.AutoMirrored.Outlined.ArrowForward, "Seta"),
            ).forEach { (t, icon, label) ->
                BarIcon(icon, label, cfg.shapeType == t) { tools.update { it.copy(shapeType = t) } }
            }
            Divider()
            WidthSlots(cfg.shapeWidths, cfg.shapeWidthIdx, 0.5f..30f,
                onSelect = { i -> tools.update { it.copy(shapeWidthIdx = i) } },
                onEdit = { i, w -> tools.update { it.copy(shapeWidths = it.shapeWidths.replaced(i, w)) } })
            Divider()
            ColorSlots(st, cfg, cfg.shapeColors, cfg.shapeColorIdx,
                onSelect = { i -> tools.update { it.copy(shapeColorIdx = i) } },
                onEdit = { i, c -> tools.update { it.copy(shapeColors = it.shapeColors.replaced(i, c)) } })
        }
        Tool.LASSO -> {
            BarIcon(Icons.Outlined.Delete, "Excluir seleção") { st.deleteSelection() }
            BarIcon(Icons.Outlined.ContentCopy, "Duplicar seleção") { st.duplicateSelection() }
            Divider()
            cfg.penColors.forEach { c ->
                Box(Modifier.size(44.dp).clickable { st.recolorSelection(c) }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(26.dp).clip(CircleShape).background(Color(c)))
                }
            }
        }
        Tool.NONE -> Unit
    }
}

