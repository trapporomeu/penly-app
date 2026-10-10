package com.trapporomeu.penlyapp.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Size3(val label: String) { SMALL("Pequeno"), MEDIUM("Médio"), LARGE("Grande") }
enum class ThemeMode(val label: String) { LIGHT("Claro"), DARK("Escuro"), SYSTEM("Do sistema") }
enum class ScrollDirection(val label: String) { HORIZONTAL("Horizontal"), VERTICAL("Vertical") }
enum class FingerMode(val label: String) {
    AUTO("Automático (dedo desenha até usar a caneta)"),
    ALWAYS("Dedo sempre desenha"),
    NEVER("Só a caneta desenha"),
}
enum class TextPlacement(val label: String) { LEFT("Esquerda"), MIDDLE("Meio") }
enum class ExportQuality(val label: String, val scale: Float) {
    LOW("Baixa", 1f), STANDARD("Padrão", 2f), HIGH("Alta", 3f)
}

data class AppSettings(
    // Geral
    val uiSize: Size3 = Size3.MEDIUM,
    val theme: ThemeMode = ThemeMode.LIGHT,
    val promptForName: Boolean = true,
    val alwaysShowFirstPage: Boolean = false,
    // Editor
    val fingerMode: FingerMode = FingerMode.AUTO,
    val scrollDirection: ScrollDirection = ScrollDirection.HORIZONTAL,
    val selectionScale: Size3 = Size3.MEDIUM,
    val imageImportSize: Size3 = Size3.MEDIUM,
    val showSystemBars: Boolean = true,
    val zoomLockButton: Boolean = true,
    val highlighterBelow: Boolean = true,
    // Texto
    val textPlacement: TextPlacement = TextPlacement.MIDDLE,
    // Exportação
    val exportQuality: ExportQuality = ExportQuality.STANDARD,
    // Backups automáticos
    val autoBackups: Boolean = true,
    val maxBackups: Int = 10,
    val actionsPerBackup: Int = 30,
    // Avançado
    val cacheSizeMb: Int = 256,
    // Estado interno
    val starterCreated: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppSettings> = _state.asStateFlow()

    val value: AppSettings get() = _state.value

    fun update(block: (AppSettings) -> AppSettings) {
        val new = block(_state.value)
        _state.value = new
        save(new)
    }

    private inline fun <reified E : Enum<E>> enumOf(key: String, def: E): E =
        prefs.getString(key, null)?.let { s -> enumValues<E>().firstOrNull { it.name == s } } ?: def

    private fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            uiSize = enumOf("uiSize", d.uiSize),
            theme = enumOf("theme", d.theme),
            promptForName = prefs.getBoolean("promptForName", d.promptForName),
            alwaysShowFirstPage = prefs.getBoolean("alwaysShowFirstPage", d.alwaysShowFirstPage),
            fingerMode = enumOf("fingerMode", d.fingerMode),
            scrollDirection = enumOf("scrollDirection", d.scrollDirection),
            selectionScale = enumOf("selectionScale", d.selectionScale),
            imageImportSize = enumOf("imageImportSize", d.imageImportSize),
            showSystemBars = prefs.getBoolean("showSystemBars", d.showSystemBars),
            zoomLockButton = prefs.getBoolean("zoomLockButton", d.zoomLockButton),
            highlighterBelow = prefs.getBoolean("highlighterBelow", d.highlighterBelow),
            textPlacement = enumOf("textPlacement", d.textPlacement),
            exportQuality = enumOf("exportQuality", d.exportQuality),
            autoBackups = prefs.getBoolean("autoBackups", d.autoBackups),
            maxBackups = prefs.getInt("maxBackups", d.maxBackups),
            actionsPerBackup = prefs.getInt("actionsPerBackup", d.actionsPerBackup),
            cacheSizeMb = prefs.getInt("cacheSizeMb", d.cacheSizeMb),
            starterCreated = prefs.getBoolean("starterCreated", d.starterCreated),
        )
    }

    private fun save(s: AppSettings) {
        prefs.edit()
            .putString("uiSize", s.uiSize.name)
            .putString("theme", s.theme.name)
            .putBoolean("promptForName", s.promptForName)
            .putBoolean("alwaysShowFirstPage", s.alwaysShowFirstPage)
            .putString("fingerMode", s.fingerMode.name)
            .putString("scrollDirection", s.scrollDirection.name)
            .putString("selectionScale", s.selectionScale.name)
            .putString("imageImportSize", s.imageImportSize.name)
            .putBoolean("showSystemBars", s.showSystemBars)
            .putBoolean("zoomLockButton", s.zoomLockButton)
            .putBoolean("highlighterBelow", s.highlighterBelow)
            .putString("textPlacement", s.textPlacement.name)
            .putString("exportQuality", s.exportQuality.name)
            .putBoolean("autoBackups", s.autoBackups)
            .putInt("maxBackups", s.maxBackups)
            .putInt("actionsPerBackup", s.actionsPerBackup)
            .putInt("cacheSizeMb", s.cacheSizeMb)
            .putBoolean("starterCreated", s.starterCreated)
            .apply()
    }
}
