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
enum class Quality3(val label: String) { LOW("Baixa"), MEDIUM("Média"), HIGH("Alta") }

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
    val backgroundQuality: Quality3 = Quality3.HIGH,
    val interactionWhileEditing: Boolean = true,
    val multiSelectTaps: Boolean = false,
    val snapRotation90: Boolean = true,
    // Sincronização
    val cloudService: String = "Desativado",
    // Entrada de texto
    val onScreenKeyboard: Boolean = false,
    val appHandwriting: Boolean = true,
    val systemHandwriting: Boolean = true,
    val handwritingLanguage: String = "Português",
    val handwritingAutoSpacing: Boolean = true,
    val textPlacement: TextPlacement = TextPlacement.MIDDLE,
    /** 0 = usar o tamanho da barra de ferramentas */
    val textBoxFontSize: Int = 0,
    // Exportação
    val exportQuality: ExportQuality = ExportQuality.STANDARD,
    // Backups automáticos
    val autoBackups: Boolean = true,
    val maxBackups: Int = 10,
    val actionsPerBackup: Int = 30,
    // Avançado
    val cacheSizeMb: Int = 256,
    val memorySafeMode: Boolean = false,
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
            backgroundQuality = enumOf("backgroundQuality", d.backgroundQuality),
            interactionWhileEditing = prefs.getBoolean("interactionWhileEditing", d.interactionWhileEditing),
            multiSelectTaps = prefs.getBoolean("multiSelectTaps", d.multiSelectTaps),
            snapRotation90 = prefs.getBoolean("snapRotation90", d.snapRotation90),
            cloudService = prefs.getString("cloudService", d.cloudService) ?: d.cloudService,
            onScreenKeyboard = prefs.getBoolean("onScreenKeyboard", d.onScreenKeyboard),
            appHandwriting = prefs.getBoolean("appHandwriting", d.appHandwriting),
            systemHandwriting = prefs.getBoolean("systemHandwriting", d.systemHandwriting),
            handwritingLanguage = prefs.getString("handwritingLanguage", d.handwritingLanguage) ?: d.handwritingLanguage,
            handwritingAutoSpacing = prefs.getBoolean("handwritingAutoSpacing", d.handwritingAutoSpacing),
            textBoxFontSize = prefs.getInt("textBoxFontSize", d.textBoxFontSize),
            memorySafeMode = prefs.getBoolean("memorySafeMode", d.memorySafeMode),
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
            .putString("backgroundQuality", s.backgroundQuality.name)
            .putBoolean("interactionWhileEditing", s.interactionWhileEditing)
            .putBoolean("multiSelectTaps", s.multiSelectTaps)
            .putBoolean("snapRotation90", s.snapRotation90)
            .putString("cloudService", s.cloudService)
            .putBoolean("onScreenKeyboard", s.onScreenKeyboard)
            .putBoolean("appHandwriting", s.appHandwriting)
            .putBoolean("systemHandwriting", s.systemHandwriting)
            .putString("handwritingLanguage", s.handwritingLanguage)
            .putBoolean("handwritingAutoSpacing", s.handwritingAutoSpacing)
            .putInt("textBoxFontSize", s.textBoxFontSize)
            .putBoolean("memorySafeMode", s.memorySafeMode)
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
