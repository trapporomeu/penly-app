package com.trapporomeu.penlyapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.trapporomeu.penlyapp.data.Size3
import com.trapporomeu.penlyapp.data.ThemeMode

val PenlyBlue = Color(0xFF5B8BD0)
val PenlyBlueDark = Color(0xFF2B3E5C)

private val Light = lightColorScheme(
    primary = PenlyBlue,
    onPrimary = Color.White,
    secondary = PenlyBlue,
    background = Color.White,
    surface = Color.White,
    surfaceVariant = Color(0xFFF4F4F4),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8DB3EA),
    onPrimary = Color(0xFF0E2340),
    secondary = Color(0xFF8DB3EA),
    background = Color(0xFF16181C),
    surface = Color(0xFF1E2126),
    surfaceVariant = Color(0xFF2A2E35),
)

@Composable
fun PenlyTheme(mode: ThemeMode, uiSize: Size3, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val factor = when (uiSize) {
        Size3.SMALL -> 0.88f
        Size3.MEDIUM -> 1f
        Size3.LARGE -> 1.15f
    }
    val d = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(d.density * factor, d.fontScale)) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, content = content)
    }
}

/** Cor da barra superior da biblioteca (azul do Penly no claro). */
@Composable
fun topBarColor(): Color =
    if (MaterialTheme.colorScheme.background == Color.White) PenlyBlue else PenlyBlueDark
