package com.trapporomeu.penlyapp.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.trapporomeu.penlyapp.model.PAGE_H
import com.trapporomeu.penlyapp.model.PAGE_W
import com.trapporomeu.penlyapp.model.Page
import com.trapporomeu.penlyapp.model.PageTemplate

val CoverColors = listOf(
    0xFFA9D09A, 0xFF7FA7D9, 0xFFF4A7B9, 0xFFF7D07A, 0xFFB39DDB, 0xFFFFAB91, 0xFF80CBC4, 0xFF90A4AE, 0xFF5D4037, 0xFF263238,
).map { it.toInt() }

val TemplateLabels = linkedMapOf(
    PageTemplate.BLANK to "Em branco",
    PageTemplate.LINED to "Pautado",
    PageTemplate.GRID to "Quadriculado",
    PageTemplate.DOTS to "Pontilhado",
    PageTemplate.TODO to "Tarefas",
    PageTemplate.WEEKLY to "Semanal",
)

fun makePage(template: PageTemplate, landscape: Boolean) =
    if (landscape) Page(width = PAGE_H, height = PAGE_W, template = template) else Page(template = template)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewNotebookDialog(defaultName: String, onDismiss: () -> Unit, onCreate: (String, List<Page>) -> Unit) {
    var name by remember { mutableStateOf(defaultName) }
    var template by remember { mutableStateOf(PageTemplate.LINED) }
    var landscape by remember { mutableStateOf(false) }
    var pageCount by remember { mutableFloatStateOf(20f) }
    var withCover by remember { mutableStateOf(true) }
    var coverColor by remember { mutableIntStateOf(CoverColors[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Novo caderno") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nome") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Modelo de página", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TemplateLabels.forEach { (t, label) ->
                        FilterChip(selected = template == t, onClick = { template = t }, label = { Text(label) })
                    }
                }
                Text("Orientação", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !landscape, onClick = { landscape = false }, label = { Text("Retrato") })
                    FilterChip(selected = landscape, onClick = { landscape = true }, label = { Text("Paisagem") })
                }
                Text("Páginas: ${pageCount.toInt()}", style = MaterialTheme.typography.labelLarge)
                Slider(pageCount, { pageCount = it }, valueRange = 1f..200f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(withCover, { withCover = it })
                    Text("Capa com o nome do caderno")
                }
                if (withCover) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        CoverColors.forEach { c ->
                            Box(
                                Modifier.size(34.dp).background(Color(c), CircleShape)
                                    .border(if (c == coverColor) 3.dp else 1.dp, if (c == coverColor) MaterialTheme.colorScheme.primary else Color.LightGray, CircleShape)
                                    .clickable { coverColor = c },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = {
                val pages = buildList {
                    if (withCover) add(makePage(PageTemplate.COVER, landscape).copy(bgColor = coverColor, title = name.trim()))
                    repeat(pageCount.toInt()) { add(makePage(template, landscape)) }
                }
                onCreate(name.trim(), pages)
            }) { Text("Criar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
