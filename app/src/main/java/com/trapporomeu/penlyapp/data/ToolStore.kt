package com.trapporomeu.penlyapp.data

import android.content.Context
import com.trapporomeu.penlyapp.model.PenType
import com.trapporomeu.penlyapp.model.ShapeType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

/** Opções das ferramentas (cores, espessuras, tipo de caneta…). Ficam salvas entre as sessões. */
@Serializable
data class ToolConfig(
    val penType: PenType = PenType.PENCIL,
    val smoothing: Int = 45,
    val penWidths: List<Float> = listOf(2f, 4f, 7f),
    val penWidthIdx: Int = 1,
    val penColors: List<Int> = listOf(0xFF616161, 0xFFE53935, 0xFF66DD44, 0xFF1010F0).map { it.toInt() },
    val penColorIdx: Int = 0,
    val hlWidths: List<Float> = listOf(16f, 28f, 40f),
    val hlWidthIdx: Int = 1,
    val hlColors: List<Int> = listOf(0xFFFFEB3B, 0xFF76FF03, 0xFF40C4FF, 0xFFFF80AB).map { it.toInt() },
    val hlColorIdx: Int = 0,
    val eraserSizes: List<Float> = listOf(8f, 16f, 32f),
    val eraserIdx: Int = 1,
    val shapeType: ShapeType = ShapeType.RECT,
    val shapeWidths: List<Float> = listOf(2f, 4f, 7f),
    val shapeWidthIdx: Int = 1,
    val shapeColors: List<Int> = listOf(0xFF616161, 0xFFE53935, 0xFF66DD44, 0xFF1010F0).map { it.toInt() },
    val shapeColorIdx: Int = 0,
    val textColors: List<Int> = listOf(0xFF212121, 0xFFE53935, 0xFF2E7D32, 0xFF1E5BC6).map { it.toInt() },
    val textColorIdx: Int = 0,
    val textSize: Float = 36f,
    val textFont: String? = null,
    val textBold: Boolean = false,
    /** Cores guardadas no seletor de cores (até 14). */
    val palette: List<Int> = listOf(0xFF8BD67E, 0xFF616161, 0xFFD9BF7A, 0xFFA56CC1, 0xFF222222, 0xFF5B86A8).map { it.toInt() },
)

class ToolStore(context: Context) {
    private val prefs = context.getSharedPreferences("tools", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<ToolConfig> = _state.asStateFlow()
    val value: ToolConfig get() = _state.value

    private fun load(): ToolConfig = try {
        prefs.getString("config", null)?.let { AppJson.decodeFromString<ToolConfig>(it) } ?: ToolConfig()
    } catch (e: Exception) {
        ToolConfig()
    }

    fun update(block: (ToolConfig) -> ToolConfig) {
        val new = block(_state.value)
        _state.value = new
        prefs.edit().putString("config", AppJson.encodeToString(new)).apply()
    }
}

fun <T> List<T>.replaced(index: Int, value: T): List<T> = mapIndexed { i, v -> if (i == index) value else v }
