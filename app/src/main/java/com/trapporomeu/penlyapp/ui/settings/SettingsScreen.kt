package com.trapporomeu.penlyapp.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.BuildConfig
import com.trapporomeu.penlyapp.data.ExportQuality
import com.trapporomeu.penlyapp.data.FingerMode
import com.trapporomeu.penlyapp.data.ScrollDirection
import com.trapporomeu.penlyapp.data.Size3
import com.trapporomeu.penlyapp.data.TextPlacement
import com.trapporomeu.penlyapp.data.ThemeMode
import com.trapporomeu.penlyapp.render.BitmapCache
import com.trapporomeu.penlyapp.render.FontRegistry
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.components.ConfirmDialog
import com.trapporomeu.penlyapp.ui.components.displayName

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val s by vm.settings.state.collectAsStateWithLifecycle()
    var confirmRemake by remember { mutableStateOf(false) }
    var fontsVersion by remember { mutableIntStateOf(0) }
    val fontPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val ok = vm.repo.addFont(uri, displayName(context, uri))
            vm.toast = if (ok) "Fonte adicionada" else "Arquivo de fonte inválido (use .ttf ou .otf)"
            fontsVersion++
        }
    }
    fun set(block: (com.trapporomeu.penlyapp.data.AppSettings) -> com.trapporomeu.penlyapp.data.AppSettings) = vm.settings.update(block)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            IconButton(onClick = { vm.back() }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, "Voltar")
            }
            Text(
                "Versão do app ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        HorizontalDivider()
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)) {
            Section("Geral")
            Choice("Tamanho da interface", Size3.entries, s.uiSize, { it.label }) { v -> set { it.copy(uiSize = v) } }
            Choice("Modo claro/escuro", ThemeMode.entries, s.theme, { it.label }) { v -> set { it.copy(theme = v) } }
            Check("Pedir nome ao criar um caderno", s.promptForName) { v -> set { it.copy(promptForName = v) } }
            Check("Sempre mostrar a primeira página na tela inicial", s.alwaysShowFirstPage) { v -> set { it.copy(alwaysShowFirstPage = v) } }

            Section("Sincronização")
            Note("A sincronização na nuvem ainda não está disponível nesta versão. Para levar um caderno para outro aparelho, exporte-o como arquivo .penlyapp.")

            Section("Editor")
            Choice("Desenhar com o dedo (Smart Pen)", FingerMode.entries, s.fingerMode, { it.label }) { v -> set { it.copy(fingerMode = v) } }
            Choice("Direção da rolagem", ScrollDirection.entries, s.scrollDirection, { it.label }) { v -> set { it.copy(scrollDirection = v) } }
            Choice("Tamanho dos controles de seleção", Size3.entries, s.selectionScale, { it.label }) { v -> set { it.copy(selectionScale = v) } }
            Choice("Tamanho das imagens importadas", Size3.entries, s.imageImportSize, { it.label }) { v -> set { it.copy(imageImportSize = v) } }
            Choice("Barras do sistema no editor", listOf(true, false), s.showSystemBars, { if (it) "Mostrar" else "Ocultar" }) { v -> set { it.copy(showSystemBars = v) } }
            Check("Mostrar botão de travar rolagem/zoom", s.zoomLockButton) { v -> set { it.copy(zoomLockButton = v) } }
            Check("Marca-texto por baixo da escrita e do texto", s.highlighterBelow) { v -> set { it.copy(highlighterBelow = v) } }

            Section("Texto")
            Choice("Posição do texto em relação ao toque", TextPlacement.entries, s.textPlacement, { it.label }) { v -> set { it.copy(textPlacement = v) } }
            Note("Converter escrita à mão em texto virá numa próxima versão.")

            Section("Fontes")
            Note("Importe arquivos de fonte (.ttf ou .otf) para usar na ferramenta de texto.")
            val fonts = remember(fontsVersion) { FontRegistry.userFonts() }
            fonts.forEach { f ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(f, Modifier.weight(1f))
                    TextButton(onClick = {
                        java.io.File(vm.repo.fontsDir, f).delete()
                        fontsVersion++
                    }) { Text("Remover") }
                }
            }
            ActionRow("Adicionar fonte", "ADICIONAR") { fontPicker.launch(arrayOf("*/*")) }

            Section("Exportação")
            Note("Se o app travar ao exportar, use uma qualidade menor.")
            Choice("Qualidade da imagem exportada", ExportQuality.entries, s.exportQuality, { it.label }) { v -> set { it.copy(exportQuality = v) } }

            Section("Backups automáticos")
            Note("Os backups automáticos ficam no menu lateral → Backups. Se você apagar o caderno ou desinstalar o app, os backups também se perdem. O jeito mais seguro de guardar um caderno é exportá-lo como arquivo .penlyapp.")
            Check("Ativar backups automáticos", s.autoBackups) { v -> set { it.copy(autoBackups = v) } }
            Choice("Número máximo de backups por caderno", listOf(3, 5, 10, 20, 50), s.maxBackups, { "$it" }) { v -> set { it.copy(maxBackups = v) } }
            Choice("Ações por backup", listOf(10, 20, 30, 50, 100), s.actionsPerBackup, { "$it" }) { v -> set { it.copy(actionsPerBackup = v) } }

            Section("Ações")
            ActionRow("Refazer os planners iniciais", "REFAZER") { confirmRemake = true }

            Section("Avançado")
            Choice("Tamanho do cache de imagens", listOf(64, 128, 256, 512), s.cacheSizeMb, { "${it}mb" }) { v ->
                set { it.copy(cacheSizeMb = v) }
                BitmapCache.configure(v)
            }
            ActionRow("Limpar cache de imagens", "LIMPAR") { BitmapCache.clear(); vm.toast = "Cache limpo" }
            Spacer(Modifier.padding(24.dp))
        }
    }

    if (confirmRemake) {
        ConfirmDialog(
            "Refazer planners iniciais?",
            "Os cadernos de exemplo serão criados de novo. Seus cadernos não serão alterados.",
            "Refazer",
            onDismiss = { confirmRemake = false },
        ) {
            confirmRemake = false
            vm.io { vm.repo.createStarterPlanners() }
            vm.toast = "Planners criados"
        }
    }
}

@Composable
private fun Section(title: String) {
    Row(Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Spacer(Modifier.width(24.dp))
        HorizontalDivider(Modifier.weight(1f))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun Check(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Checkbox(value, onChange)
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, value: T, text: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Box {
            TextButton(onClick = { open = true }) {
                Text(text(value), color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.End)
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { o ->
                    DropdownMenuItem(text = { Text(text(o)) }, onClick = { open = false; onChange(o) })
                }
            }
        }
    }
}

@Composable
private fun ActionRow(label: String, button: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Button(
            onClick = onClick,
            shape = MaterialTheme.shapes.extraSmall,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE0E0E0), contentColor = Color(0xFF424242)),
        ) { Text(button) }
    }
}
