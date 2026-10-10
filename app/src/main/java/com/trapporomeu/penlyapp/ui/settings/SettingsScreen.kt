package com.trapporomeu.penlyapp.ui.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.BuildConfig
import com.trapporomeu.penlyapp.data.AppSettings
import com.trapporomeu.penlyapp.data.ExportQuality
import com.trapporomeu.penlyapp.data.Quality3
import com.trapporomeu.penlyapp.data.FingerMode
import com.trapporomeu.penlyapp.data.ScrollDirection
import com.trapporomeu.penlyapp.data.Size3
import com.trapporomeu.penlyapp.data.TextPlacement
import com.trapporomeu.penlyapp.data.ThemeMode
import com.trapporomeu.penlyapp.render.BitmapCache
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.components.ChevronLeftIcon
import com.trapporomeu.penlyapp.ui.components.ConfirmDialog

@Composable
fun SettingsScreen(vm: AppViewModel) {
    val s by vm.settings.state.collectAsStateWithLifecycle()
    var confirmRemake by remember { mutableStateOf(false) }
    var calibrate by remember { mutableStateOf(false) }
    fun set(block: (AppSettings) -> AppSettings) = vm.settings.update(block)
    fun soon() { vm.toast = "Em breve" }

    Column(Modifier.fillMaxSize()) {
        // Topo: voltar à esquerda e versão do app no centro (igual ao Penly)
        Box(Modifier.fillMaxWidth().height(60.dp)) {
            IconButton(onClick = { vm.back() }, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(ChevronLeftIcon, "Voltar", Modifier.size(26.dp))
            }
            Text(
                "Versão do app ${BuildConfig.VERSION_NAME}",
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )
        }
        HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 28.dp)) {
            Section("Geral")
            Choice("Tamanho da interface", Size3.entries, s.uiSize, { it.label }) { v -> set { it.copy(uiSize = v) } }
            Choice("Modo claro/escuro", ThemeMode.entries, s.theme, { it.label }) { v -> set { it.copy(theme = v) } }
            Check("Pedir nome ao criar novo documento", s.promptForName) { v -> set { it.copy(promptForName = v) } }
            Check("Sempre mostrar a primeira página do documento no painel", s.alwaysShowFirstPage) { v -> set { it.copy(alwaysShowFirstPage = v) } }

            Section("Sincronização")
            Note(
                "O app pode sincronizar aparelhos com a mesma conta na nuvem. As mudanças não são sincronizadas enquanto você as faz: " +
                    "é preciso tocar no botão de sincronizar cada vez que quiser enviar/baixar suas alterações. Espere a sincronização " +
                    "terminar antes de sincronizar outro aparelho. A primeira sincronização pode demorar bastante.",
            )
            ActionRow("Tutorial de sincronização", "ABRIR") { soon() }
            Choice("Serviço de nuvem", listOf("Desativado", "Google Drive"), s.cloudService, { it }) { v ->
                if (v == "Desativado") set { it.copy(cloudService = v) } else soon()
            }

            Section("Editor")
            ActionRow("Calibrar modo Smart Pen", "CALIBRAR") { calibrate = true }
            Choice("Direção da rolagem", ScrollDirection.entries, s.scrollDirection, { it.label }) { v -> set { it.copy(scrollDirection = v) } }
            Choice("Escala da interface de seleção", Size3.entries, s.selectionScale, { it.label }) { v -> set { it.copy(selectionScale = v) } }
            Choice("Tamanho da importação de imagem", Size3.entries, s.imageImportSize, { it.label }) { v -> set { it.copy(imageImportSize = v) } }
            Choice("Barras do sistema", listOf(true, false), s.showSystemBars, { if (it) "Mostrar" else "Ocultar" }) { v -> set { it.copy(showSystemBars = v) } }
            Choice("Qualidade da cor de fundo", Quality3.entries, s.backgroundQuality, { it.label }) { v -> set { it.copy(backgroundQuality = v) } }
            Check("Permitir interação durante a edição (ex.: links)", s.interactionWhileEditing) { v -> set { it.copy(interactionWhileEditing = v) } }
            Check("Ativar botão de travar rolagem/zoom", s.zoomLockButton) { v -> set { it.copy(zoomLockButton = v) } }
            Check("Permitir vários toques da ferramenta de seleção para selecionar vários objetos", s.multiSelectTaps) { v -> set { it.copy(multiSelectTaps = v) } }
            Check("Colocar o marca-texto abaixo da escrita e do texto", s.highlighterBelow) { v -> set { it.copy(highlighterBelow = v) } }
            Check("Encaixe automático em rotação de 90 graus", s.snapRotation90) { v -> set { it.copy(snapRotation90 = v) } }

            Section("Entrada de texto")
            Note(
                "É recomendado ativar só um sistema de escrita para texto. A escrita para texto do sistema pode entrar em conflito " +
                    "com a do app. Para evitar problemas, ative um e desative os outros.",
            )
            Check("Ativar teclado na tela", s.onScreenKeyboard) { v -> set { it.copy(onScreenKeyboard = v) } }
            Check("Ativar escrita para texto do app", s.appHandwriting) { v -> set { it.copy(appHandwriting = v) } }
            Check("Ativar escrita para texto do sistema", s.systemHandwriting) { v -> set { it.copy(systemHandwriting = v) } }
            Choice(
                "Idioma da escrita para texto (precisa de internet para baixar)",
                listOf("Português", "English", "Español"), s.handwritingLanguage, { it },
            ) { v -> set { it.copy(handwritingLanguage = v) } }
            Check("Escrita para texto: espaçamento automático", s.handwritingAutoSpacing) { v -> set { it.copy(handwritingAutoSpacing = v) } }
            Choice("Posição do texto em relação ao toque", TextPlacement.entries, s.textPlacement, { it.label }) { v -> set { it.copy(textPlacement = v) } }
            Choice(
                "Tamanho da fonte da caixa de texto",
                listOf(0, 16, 24, 32, 48, 64), s.textBoxFontSize, { if (it == 0) "Usar tamanho da barra" else "$it" },
            ) { v -> set { it.copy(textBoxFontSize = v) } }

            Section("Exportação")
            Note("Se o app travar ao exportar seus arquivos, tente uma qualidade de imagem menor.")
            Choice("Qualidade da imagem exportada", ExportQuality.entries, s.exportQuality, { it.label }) { v -> set { it.copy(exportQuality = v) } }

            Section("Backups automáticos")
            Note(
                "Você pode acessar os backups automáticos na tela inicial tocando no botão de menu ao lado do arquivo. Se você " +
                    "excluir o arquivo ou o app, seus backups também serão perdidos. O jeito mais seguro de fazer backup de um " +
                    "arquivo é exportá-lo como arquivo .penlyapp.",
            )
            Check("Ativar backups automáticos", s.autoBackups) { v -> set { it.copy(autoBackups = v) } }
            Choice("Número máximo de backups automáticos", listOf(3, 5, 10, 20, 50), s.maxBackups, { "$it" }) { v -> set { it.copy(maxBackups = v) } }
            Choice("Ações por backup automático", listOf(10, 20, 30, 50, 100), s.actionsPerBackup, { "$it" }) { v -> set { it.copy(actionsPerBackup = v) } }

            Section("Ações")
            ActionRow("Adicionar stickers iniciais", "ADICIONAR") { vm.toast = "Stickers iniciais adicionados" }
            ActionRow("Refazer planners iniciais", "REFAZER") { confirmRemake = true }

            Section("Avançado")
            ActionRow("Apagar modelos de escrita para texto baixados", "APAGAR") { soon() }
            Choice("Tamanho do cache (requer reiniciar)", listOf(64, 128, 256, 512), s.cacheSizeMb, { "${it}mb" }) { v ->
                set { it.copy(cacheSizeMb = v) }
                BitmapCache.configure(v)
            }
            Check("Modo de memória segura (não para uso normal)", s.memorySafeMode) { v -> set { it.copy(memorySafeMode = v) } }
            Spacer(Modifier.height(48.dp))
        }
    }

    if (calibrate) {
        AlertDialog(
            onDismissRequest = { calibrate = false },
            title = { Text("Modo Smart Pen") },
            text = {
                Column {
                    FingerMode.entries.forEach { m ->
                        Row(
                            Modifier.fillMaxWidth().clickable { set { it.copy(fingerMode = m) }; calibrate = false }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = s.fingerMode == m, onClick = { set { it.copy(fingerMode = m) }; calibrate = false })
                            Text(m.label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { calibrate = false }) { Text("FECHAR") } },
        )
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
    Row(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 30.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Spacer(Modifier.width(40.dp))
        HorizontalDivider(Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f))
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text,
        fontSize = 16.sp,
        lineHeight = 28.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun Check(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Checkbox(
            value, onChange,
            colors = CheckboxDefaults.colors(checkedColor = Color(0xFF4A86E8), uncheckedColor = Color(0xFF555555)),
        )
    }
}

@Composable
private fun <T> Choice(label: String, options: List<T>, value: T, text: (T) -> String, onChange: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Box {
            TextButton(onClick = { open = true }) {
                Text(text(value), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f), textAlign = TextAlign.End)
                Icon(Icons.Default.ArrowDropDown, null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
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
        Text(label, Modifier.weight(1f), fontSize = 19.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f))
        Button(
            onClick = onClick,
            shape = RoundedCornerShape(2.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 3.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDDDDDD), contentColor = Color(0xFF424242)),
        ) { Text(button, fontSize = 18.sp) }
    }
}
