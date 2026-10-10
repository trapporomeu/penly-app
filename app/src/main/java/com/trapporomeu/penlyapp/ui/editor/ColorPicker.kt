package com.trapporomeu.penlyapp.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private fun hsv(color: Int): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(color, it) }
private fun hsvToColor(h: Float, s: Float, v: Float): Int = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
fun hexOf(color: Int) = String.format("#%06X", color and 0xFFFFFF)

/**
 * Seletor de cores como o do Penly: roda de cores, barra de brilho, código hex,
 * conta-gotas e uma paleta de cores guardadas (toque no + para guardar a cor atual,
 * segure uma cor guardada para removê-la).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ColorPicker(
    color: Int,
    palette: List<Int>,
    onColor: (Int) -> Unit,
    onPaletteChange: (List<Int>) -> Unit,
    onEyedropper: () -> Unit,
) {
    val initial = remember { hsv(color) }
    var hue by remember { mutableFloatStateOf(initial[0]) }
    var sat by remember { mutableFloatStateOf(initial[1]) }
    var value by remember { mutableFloatStateOf(initial[2]) }
    var hexText by remember { mutableStateOf(hexOf(color)) }

    fun emit() {
        val c = hsvToColor(hue, sat, value)
        hexText = hexOf(c)
        onColor(c)
    }

    fun setFromColor(c: Int) {
        val h = hsv(c)
        hue = h[0]; sat = h[1]; value = h[2]
        hexText = hexOf(c)
        onColor(c or 0xFF000000.toInt())
    }

    Row(Modifier.padding(16.dp)) {
        Column {
            // Roda de cores (matiz em volta, saturação do centro para fora)
            Canvas(
                Modifier.size(220.dp)
                    .pointerInput(Unit) {
                        fun pick(o: Offset) {
                            val r = min(size.width, size.height) / 2f
                            val dx = o.x - size.width / 2f
                            val dy = o.y - size.height / 2f
                            var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                            if (a < 0) a += 360f
                            hue = a
                            sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                            emit()
                        }
                        detectTapGestures { pick(it) }
                    }
                    .pointerInput(Unit) {
                        fun pick(o: Offset) {
                            val r = min(size.width, size.height) / 2f
                            val dx = o.x - size.width / 2f
                            val dy = o.y - size.height / 2f
                            var a = Math.toDegrees(atan2(dy, dx).toDouble()).toFloat()
                            if (a < 0) a += 360f
                            hue = a
                            sat = (hypot(dx, dy) / r).coerceIn(0f, 1f)
                            emit()
                        }
                        detectDragGestures(onDragStart = { pick(it) }) { ch, _ -> pick(ch.position) }
                    },
            ) {
                val r = size.minDimension / 2f
                val hues = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { Color(hsvToColor(it, 1f, 1f)) }
                drawCircle(Brush.sweepGradient(hues, center), r)
                drawCircle(Brush.radialGradient(listOf(Color.White, Color.White.copy(alpha = 0f)), center, r), r)
                drawCircle(Color.Black.copy(alpha = 1f - value), r)
                val rad = Math.toRadians(hue.toDouble())
                val cx = center.x + (cos(rad) * sat * r).toFloat()
                val cy = center.y + (sin(rad) * sat * r).toFloat()
                val mark = if (value > 0.5f) Color.Black else Color.White
                drawCircle(mark, 9.dp.toPx(), Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                drawLine(mark, Offset(cx - 13.dp.toPx(), cy), Offset(cx + 13.dp.toPx(), cy), 1.5.dp.toPx())
                drawLine(mark, Offset(cx, cy - 13.dp.toPx()), Offset(cx, cy + 13.dp.toPx()), 1.5.dp.toPx())
            }
            Spacer(Modifier.height(18.dp))
            // Barra de brilho
            Canvas(
                Modifier.width(220.dp).height(34.dp)
                    .pointerInput(Unit) {
                        detectTapGestures { value = (it.x / size.width).coerceIn(0f, 1f); emit() }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = { value = (it.x / size.width).coerceIn(0f, 1f); emit() }) { ch, _ ->
                            value = (ch.position.x / size.width).coerceIn(0f, 1f); emit()
                        }
                    },
            ) {
                val barTop = 10.dp.toPx()
                drawRect(
                    Brush.horizontalGradient(listOf(Color.Black, Color(hsvToColor(hue, sat, 1f)))),
                    topLeft = Offset(0f, barTop),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - barTop),
                )
                drawRect(Color.Gray, Offset(0f, barTop), androidx.compose.ui.geometry.Size(size.width, size.height - barTop), style = Stroke(1f))
                val x = value * size.width
                val tri = Path().apply {
                    moveTo(x - 7.dp.toPx(), 0f); lineTo(x + 7.dp.toPx(), 0f); lineTo(x, barTop); close()
                }
                drawPath(tri, Color.Black)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = hexText,
                    onValueChange = { t ->
                        hexText = t
                        val clean = t.trim().removePrefix("#")
                        if (clean.length == 6) clean.toLongOrNull(16)?.let { setFromColor((it or 0xFF000000).toInt()) }
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier.width(170.dp),
                )
                IconButton(onClick = onEyedropper) { Icon(Icons.Outlined.Colorize, "Conta-gotas") }
            }
        }
        Spacer(Modifier.width(20.dp))
        // Paleta de cores guardadas: 2 colunas x 7 linhas
        val slots = 14
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            for (row in 0 until slots / 2) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    for (col in 0..1) {
                        val i = row * 2 + col
                        val c = palette.getOrNull(i)
                        if (c != null) {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(c))
                                    .border(1.dp, Color(0x22000000), CircleShape)
                                    .combinedClickable(
                                        onClick = { setFromColor(c) },
                                        onLongClick = { onPaletteChange(palette.filterIndexed { j, _ -> j != i }) },
                                    ),
                            )
                        } else {
                            Box(
                                Modifier.size(36.dp).clip(CircleShape).background(Color(0xFFDDDDDD))
                                    .combinedClickable(onClick = {
                                        if (palette.size < slots) onPaletteChange(palette + hsvToColor(hue, sat, value))
                                    }),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.Add, "Guardar cor", tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(color) {
        // Atualiza quando a cor muda por fora (ex.: conta-gotas)
        val c = hsvToColor(hue, sat, value)
        if ((c and 0xFFFFFF) != (color and 0xFFFFFF)) {
            val h = hsv(color)
            hue = h[0]; sat = h[1]; value = h[2]
            hexText = hexOf(color)
        }
    }
}

