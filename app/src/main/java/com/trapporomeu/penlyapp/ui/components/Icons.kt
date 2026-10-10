package com.trapporomeu.penlyapp.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun outline(name: String, block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = block,
        )
    }.build()

/** Borracha (desenhada como no Penly: bloco inclinado com base). */
val EraserIcon: ImageVector = outline("Eraser") {
    moveTo(14.5f, 3.5f)
    lineTo(21f, 10f)
    lineTo(12f, 19f)
    lineTo(7f, 19f)
    lineTo(3.5f, 15.5f)
    close()
    moveTo(8.5f, 9.5f)
    lineTo(15f, 16f)
    moveTo(12f, 19f)
    lineTo(20.5f, 19f)
}

/** Caneta-tinteiro / lápis. */
val PenIcon: ImageVector = outline("Pen") {
    moveTo(16.5f, 3.5f)
    lineTo(20.5f, 7.5f)
    lineTo(8f, 20f)
    lineTo(3.5f, 20.5f)
    lineTo(4f, 16f)
    close()
    moveTo(14.5f, 5.5f)
    lineTo(18.5f, 9.5f)
}

/** Marca-texto. */
val HighlighterIcon: ImageVector = outline("Highlighter") {
    moveTo(15f, 3f)
    lineTo(21f, 9f)
    lineTo(12f, 18f)
    lineTo(6f, 12f)
    close()
    moveTo(6f, 12f)
    lineTo(4f, 17f)
    lineTo(7f, 20f)
    lineTo(12f, 18f)
    moveTo(4f, 17f)
    lineTo(2.5f, 20.5f)
    lineTo(6f, 20.5f)
}

/** Caneta-tinteiro (pena). */
val FountainPenIcon: ImageVector = outline("FountainPen") {
    moveTo(19f, 3f)
    lineTo(21f, 5f)
    lineTo(15.5f, 10.5f)
    moveTo(15.5f, 10.5f)
    lineTo(9f, 9.5f)
    lineTo(3.5f, 20.5f)
    lineTo(14.5f, 15f)
    close()
    moveTo(3.5f, 20.5f)
    lineTo(9.5f, 14.5f)
    moveTo(11f, 13.5f)
    lineTo(10f, 14f)
}

/** Pincel. */
val BrushIcon: ImageVector = outline("Brush") {
    moveTo(20.5f, 3.5f)
    lineTo(11f, 13f)
    moveTo(11f, 13f)
    lineTo(13f, 15f)
    moveTo(9.5f, 14.5f)
    curveTo(7f, 14.5f, 6f, 16f, 6f, 17.5f)
    curveTo(6f, 19f, 5f, 20f, 3.5f, 20.5f)
    curveTo(7f, 21f, 11.5f, 20f, 11.5f, 16.5f)
    close()
}

/** Suavização (curva). */
val SmoothingIcon: ImageVector = outline("Smoothing") {
    moveTo(3f, 16f)
    curveTo(6f, 2f, 9f, 2f, 12f, 12f)
    curveTo(15f, 22f, 18f, 22f, 21f, 8f)
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.roundRect(l: Float, t: Float, r: Float, b: Float, rad: Float) {
    moveTo(l + rad, t)
    lineTo(r - rad, t)
    arcTo(rad, rad, 0f, false, true, r, t + rad)
    lineTo(r, b - rad)
    arcTo(rad, rad, 0f, false, true, r - rad, b)
    lineTo(l + rad, b)
    arcTo(rad, rad, 0f, false, true, l, b - rad)
    lineTo(l, t + rad)
    arcTo(rad, rad, 0f, false, true, l + rad, t)
    close()
}

private fun androidx.compose.ui.graphics.vector.PathBuilder.circle(cx: Float, cy: Float, r: Float) {
    moveTo(cx - r, cy)
    arcTo(r, r, 0f, true, true, cx + r, cy)
    arcTo(r, r, 0f, true, true, cx - r, cy)
    close()
}

// ---------- Ícones finos no estilo do Penly ----------

val ChevronLeftIcon: ImageVector = outline("ChevronLeft") {
    moveTo(15f, 4f); lineTo(7f, 12f); lineTo(15f, 20f)
}

val ChevronDownIcon: ImageVector = outline("ChevronDown") {
    moveTo(4f, 8.5f); lineTo(12f, 16f); lineTo(20f, 8.5f)
}

val PagesGridIcon: ImageVector = outline("PagesGrid") {
    roundRect(3.5f, 3.5f, 10.5f, 10.5f, 1.6f)
    roundRect(13.5f, 3.5f, 20.5f, 10.5f, 1.6f)
    roundRect(3.5f, 13.5f, 10.5f, 20.5f, 1.6f)
    roundRect(13.5f, 13.5f, 20.5f, 20.5f, 1.6f)
}

val PlusIcon: ImageVector = outline("Plus") {
    moveTo(12f, 4f); lineTo(12f, 20f)
    moveTo(4f, 12f); lineTo(20f, 12f)
}

/** "T" com serifas, como o da ferramenta de texto do Penly. */
val TextToolIcon: ImageVector = outline("TextTool") {
    moveTo(5f, 7.5f); lineTo(5f, 4.5f); lineTo(19f, 4.5f); lineTo(19f, 7.5f)
    moveTo(12f, 4.5f); lineTo(12f, 19.5f)
    moveTo(9f, 19.5f); lineTo(15f, 19.5f)
}

/** Triângulo em cima, quadrado e círculo embaixo. */
val ShapesIcon: ImageVector = outline("Shapes") {
    moveTo(12f, 2.5f); lineTo(16.3f, 9.8f); lineTo(7.7f, 9.8f); close()
    roundRect(3f, 13f, 10.5f, 20.5f, 0.6f)
    circle(17f, 16.8f, 3.8f)
}

val ImageToolIcon: ImageVector = outline("ImageTool") {
    roundRect(3f, 3f, 21f, 21f, 2f)
    moveTo(3.5f, 17.5f); lineTo(9f, 12f); lineTo(13f, 16f); lineTo(15.5f, 13.5f); lineTo(20.5f, 18.5f)
    circle(15.5f, 8.2f, 1.7f)
}

/** Estrela dentro de um quadrado arredondado. */
val StickerIcon: ImageVector = outline("Sticker") {
    roundRect(3f, 3f, 21f, 21f, 4.5f)
    moveTo(12.0f, 7.0f)
    lineTo(13.47f, 10.58f)
    lineTo(17.33f, 10.87f)
    lineTo(14.38f, 13.37f)
    lineTo(15.29f, 17.13f)
    lineTo(12.0f, 15.1f)
    lineTo(8.71f, 17.13f)
    lineTo(9.62f, 13.37f)
    lineTo(6.67f, 10.87f)
    lineTo(10.53f, 10.58f)
    close()
}

/** Seleção: retângulo tracejado com cursor. */
val LassoIcon: ImageVector = ImageVector.Builder("Lasso", 24.dp, 24.dp, 24f, 24f).apply {
    path(stroke = SolidColor(Color.Black), strokeLineWidth = 1.5f, strokeLineCap = StrokeCap.Butt) {
        // retângulo tracejado de (3,3) a (16,16)
        var x = 3f
        while (x < 16f) { moveTo(x, 3f); lineTo(minOf(x + 2f, 16f), 3f); x += 3.25f }
        var y = 3f
        while (y < 16f) { moveTo(3f, y); lineTo(3f, minOf(y + 2f, 16f)); y += 3.25f }
        y = 3f
        while (y < 11f) { moveTo(16f, y); lineTo(16f, minOf(y + 2f, 11f)); y += 3.25f }
        x = 3f
        while (x < 11f) { moveTo(x, 16f); lineTo(minOf(x + 2f, 11f), 16f); x += 3.25f }
    }
    path(fill = SolidColor(Color.Black)) {
        moveTo(12f, 11f); lineTo(21.5f, 14.8f); lineTo(17.4f, 16.2f); lineTo(20.6f, 20f)
        lineTo(19.2f, 21.3f); lineTo(15.9f, 17.6f); lineTo(13.6f, 21f); close()
    }
}.build()

/** Ordenar: linhas decrescentes com seta para baixo. */
val SortIcon: ImageVector = outline("Sort") {
    moveTo(3f, 6f); lineTo(14f, 6f)
    moveTo(3f, 12f); lineTo(11f, 12f)
    moveTo(3f, 18f); lineTo(8f, 18f)
    moveTo(18f, 4.5f); lineTo(18f, 19.5f)
    moveTo(14.8f, 16.3f); lineTo(18f, 19.5f); lineTo(21.2f, 16.3f)
}
