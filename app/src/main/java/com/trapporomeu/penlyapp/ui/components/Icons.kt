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
