package com.trapporomeu.penlyapp.ui.library

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.model.BackupInfo
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.components.ConfirmDialog
import com.trapporomeu.penlyapp.ui.components.formatDate
import com.trapporomeu.penlyapp.ui.theme.PenlyBlue
import com.trapporomeu.penlyapp.ui.theme.topBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrawerScaffold(
    vm: AppViewModel,
    title: String,
    actions: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit,
) {
    AppDrawer(vm, title) { openDrawer ->
        Scaffold(topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor(),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
                navigationIcon = { IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, "Menu") } },
                title = { Text(title) },
                actions = { actions() },
            )
        }) { padding -> content(Modifier.padding(padding)) }
    }
}

@Composable
private fun EmptyMessage(text: String, modifier: Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    }
}

@Composable
fun TrashScreen(vm: AppViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    var confirmEmpty by remember { mutableStateOf(false) }
    val folders = library.folders.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }
    val notebooks = library.notebooks.filter { it.deletedAt != null }.sortedByDescending { it.deletedAt }

    DrawerScaffold(vm, "Lixeira", actions = {
        if (folders.isNotEmpty() || notebooks.isNotEmpty()) {
            TextButton(onClick = { confirmEmpty = true }) { Text("ESVAZIAR", color = Color.White) }
        }
    }) { modifier ->
        if (folders.isEmpty() && notebooks.isEmpty()) {
            EmptyMessage("A lixeira está vazia", modifier)
        } else {
            LazyColumn(modifier) {
                items(folders, key = { "f" + it.id }) { f ->
                    TrashRow(
                        icon = { Icon(Icons.Default.Folder, null, tint = PenlyBlue) },
                        title = f.name,
                        subtitle = "Pasta • excluída ${formatDate(f.deletedAt!!)}",
                        onRestore = { vm.io { vm.repo.restoreFolder(f.id) } },
                        onDelete = { vm.io { vm.repo.deleteFolderForever(f.id) } },
                    )
                }
                items(notebooks, key = { "n" + it.id }) { n ->
                    TrashRow(
                        icon = { Icon(Icons.Outlined.Book, null) },
                        title = n.name,
                        subtitle = "${n.pageCount} páginas • excluído ${formatDate(n.deletedAt!!)}",
                        onRestore = { vm.io { vm.repo.restoreNotebook(n.id) } },
                        onDelete = { vm.io { vm.repo.deleteNotebookForever(n.id) } },
                    )
                }
            }
        }
    }

    if (confirmEmpty) {
        ConfirmDialog(
            "Esvaziar a lixeira?",
            "Tudo na lixeira (e os backups desses cadernos) será apagado para sempre.",
            "Esvaziar",
            onDismiss = { confirmEmpty = false },
        ) {
            confirmEmpty = false
            vm.io { vm.repo.emptyTrash() }
        }
    }
}

@Composable
private fun TrashRow(icon: @Composable () -> Unit, title: String, subtitle: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ListItem(
        leadingContent = icon,
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = {
            Row {
                TextButton(onClick = onRestore) { Text("Restaurar") }
                TextButton(onClick = { confirm = true }) { Text("Excluir", color = MaterialTheme.colorScheme.error) }
            }
        },
    )
    HorizontalDivider()
    if (confirm) {
        ConfirmDialog("Excluir para sempre?", "\"$title\" não poderá ser recuperado.", "Excluir", onDismiss = { confirm = false }) {
            confirm = false
            onDelete()
        }
    }
}

@Composable
fun BackupsScreen(vm: AppViewModel) {
    val library by vm.library.collectAsStateWithLifecycle()
    var backups by remember { mutableStateOf<List<BackupInfo>?>(null) }
    var toRestore by remember { mutableStateOf<BackupInfo?>(null) }
    LaunchedEffect(library) {
        backups = withContext(Dispatchers.IO) { vm.repo.listBackups() }
    }
    val names = library.notebooks.associate { it.id to it.name }

    DrawerScaffold(vm, "Backups") { modifier ->
        val list = backups
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyMessage(
                "Nenhum backup ainda.\nOs backups são feitos automaticamente enquanto você escreve.",
                modifier,
            )
            else -> LazyColumn(modifier) {
                item {
                    Text(
                        "Backups automáticos ficam guardados no app. Se o app for desinstalado eles se perdem — " +
                            "para guardar de verdade, exporte o caderno como arquivo .penlyapp.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
                items(list, key = { it.notebookId + it.file }) { b ->
                    ListItem(
                        headlineContent = { Text(names[b.notebookId] ?: "Caderno") },
                        supportingContent = { Text(formatDate(b.time)) },
                        trailingContent = { TextButton(onClick = { toRestore = b }) { Text("Restaurar") } },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    toRestore?.let { b ->
        ConfirmDialog(
            "Restaurar backup?",
            "\"${names[b.notebookId]}\" voltará a ficar como estava em ${formatDate(b.time)}. " +
                "O estado atual também é guardado como backup.",
            "Restaurar",
            onDismiss = { toRestore = null },
        ) {
            toRestore = null
            vm.restoreBackup(b)
        }
    }
}

