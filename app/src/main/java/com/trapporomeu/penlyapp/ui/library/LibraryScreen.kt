package com.trapporomeu.penlyapp.ui.library

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.model.Folder
import com.trapporomeu.penlyapp.model.NotebookMeta
import com.trapporomeu.penlyapp.model.PageTemplate
import com.trapporomeu.penlyapp.render.Exporter
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.Screen
import com.trapporomeu.penlyapp.ui.SortMode
import com.trapporomeu.penlyapp.ui.components.ConfirmDialog
import com.trapporomeu.penlyapp.ui.components.TextInputDialog
import com.trapporomeu.penlyapp.ui.components.displayName
import com.trapporomeu.penlyapp.ui.components.formatDate
import com.trapporomeu.penlyapp.ui.theme.PenlyBlue
import com.trapporomeu.penlyapp.ui.theme.topBarColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed class Dialog {
    data object NewNotebook : Dialog()
    data object NewFolder : Dialog()
    data class RenameNotebook(val meta: NotebookMeta) : Dialog()
    data class RenameFolder(val folder: Folder) : Dialog()
    data class MoveNotebook(val meta: NotebookMeta) : Dialog()
    data class MoveFolder(val folder: Folder) : Dialog()
    data class NamePdf(val uri: android.net.Uri, val name: String) : Dialog()
    data class TrashFolder(val folder: Folder) : Dialog()
    data object Help : Dialog()
}

@Composable
fun AppDrawer(vm: AppViewModel, selected: String, content: @Composable (openDrawer: () -> Unit) -> Unit) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(Modifier.width(300.dp)) {
                Surface(Modifier.padding(20.dp).size(72.dp), shape = MaterialTheme.shapes.large, shadowElevation = 2.dp) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Notes, null, Modifier.size(44.dp), tint = PenlyBlue)
                    }
                }
                listOf(
                    Triple("Minhas Notas", Icons.Outlined.Notes, Screen.Library()),
                    Triple("Backups", Icons.Outlined.History, Screen.Backups),
                    Triple("Lixeira", Icons.Outlined.Delete, Screen.Trash),
                ).forEach { (label, icon, screen) ->
                    NavigationDrawerItem(
                        label = { Text(label) },
                        icon = { Icon(icon, null) },
                        selected = label == selected,
                        onClick = {
                            scope.launch { drawerState.close() }
                            if (label != selected) vm.switchRoot(screen)
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }
        },
    ) {
        content { scope.launch { drawerState.open() } }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(vm: AppViewModel, folderId: String?) {
    val context = LocalContext.current
    val library by vm.library.collectAsStateWithLifecycle()
    val settings by vm.settings.state.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<Dialog?>(null) }
    var fabMenu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val folder = library.folders.firstOrNull { it.id == folderId }
    val liveFolders = library.folders.filter { it.deletedAt == null }
    val liveFolderIds = liveFolders.map { it.id }.toSet()

    val q = query.trim().lowercase()
    val folders = liveFolders
        .filter { if (q.isNotEmpty()) it.name.lowercase().contains(q) else it.parentId == folderId }
        .let { list ->
            when (vm.sortMode) {
                SortMode.NAME -> list.sortedBy { it.name.lowercase() }
                SortMode.CREATED -> list.sortedByDescending { it.createdAt }
                SortMode.UPDATED -> list.sortedByDescending { it.updatedAt }
            }
        }
    val notebooks = library.notebooks
        .filter { it.deletedAt == null && (it.folderId == null || it.folderId in liveFolderIds) }
        .filter { if (q.isNotEmpty()) it.name.lowercase().contains(q) else it.folderId == folderId }
        .let { list ->
            when (vm.sortMode) {
                SortMode.NAME -> list.sortedBy { it.name.lowercase() }
                SortMode.CREATED -> list.sortedByDescending { it.createdAt }
                SortMode.UPDATED -> list.sortedByDescending { it.updatedAt }
            }
        }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val n = displayName(context, uri)?.removeSuffix(".pdf")?.removeSuffix(".PDF") ?: "PDF importado"
            dialog = Dialog.NamePdf(uri, n)
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importNotebookFile(uri, folderId)
    }

    AppDrawer(vm, "Minhas Notas") { openDrawer ->
        Scaffold(
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = topBarColor(),
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    ),
                    navigationIcon = {
                        if (folderId != null) {
                            IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar") }
                        } else {
                            IconButton(onClick = openDrawer) { Icon(Icons.Default.Menu, "Menu") }
                        }
                    },
                    title = {
                        if (searching) {
                            TextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = { Text("Buscar…", color = Color.White.copy(alpha = 0.7f)) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    cursorColor = Color.White,
                                    focusedIndicatorColor = Color.White,
                                ),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            Text(folder?.name ?: "Minhas Notas")
                        }
                    },
                    actions = {
                        Box {
                            IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Ordenar") }
                            DropdownMenu(sortMenu, { sortMenu = false }) {
                                SortMode.entries.forEach { m ->
                                    DropdownMenuItem(
                                        text = { Text((if (m == vm.sortMode) "✓ " else "    ") + m.label) },
                                        onClick = { vm.sortMode = m; sortMenu = false },
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                            Icon(if (searching) Icons.Default.Close else Icons.Outlined.Search, "Buscar")
                        }
                        IconButton(onClick = { dialog = Dialog.Help }) { Icon(Icons.Outlined.HelpOutline, "Ajuda") }
                        IconButton(onClick = { vm.navigate(Screen.Settings) }) { Icon(Icons.Outlined.Settings, "Configurações") }
                    },
                )
            },
            floatingActionButton = {
                Box {
                    FloatingActionButton(
                        onClick = { fabMenu = true },
                        containerColor = PenlyBlue,
                        contentColor = Color.White,
                        shape = androidx.compose.foundation.shape.CircleShape,
                    ) { Icon(Icons.Default.Add, "Novo", Modifier.size(32.dp)) }
                    DropdownMenu(fabMenu, { fabMenu = false }) {
                        DropdownMenuItem(text = { Text("Novo caderno") }, onClick = {
                            fabMenu = false
                            if (settings.promptForName) dialog = Dialog.NewNotebook
                            else vm.createNotebook("Caderno ${library.notebooks.size + 1}", folderId, List(20) { makePage(PageTemplate.LINED, false) })
                        })
                        DropdownMenuItem(text = { Text("Nova pasta") }, onClick = { fabMenu = false; dialog = Dialog.NewFolder })
                        DropdownMenuItem(text = { Text("Importar PDF") }, onClick = {
                            fabMenu = false
                            pdfPicker.launch(arrayOf("application/pdf"))
                        })
                        DropdownMenuItem(text = { Text("Importar arquivo .penlyapp") }, onClick = {
                            fabMenu = false
                            filePicker.launch(arrayOf("*/*"))
                        })
                    }
                }
            },
        ) { padding ->
            if (folders.isEmpty() && notebooks.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(
                        if (q.isNotEmpty()) "Nada encontrado" else "Nenhum caderno aqui ainda.\nToque em + para criar.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(190.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.padding(padding),
                ) {
                    items(folders, key = { "f" + it.id }) { f ->
                        FolderCard(
                            folder = f,
                            onOpen = { query = ""; searching = false; vm.navigate(Screen.Library(f.id)) },
                            onRename = { dialog = Dialog.RenameFolder(f) },
                            onMove = { dialog = Dialog.MoveFolder(f) },
                            onDelete = { dialog = Dialog.TrashFolder(f) },
                        )
                    }
                    items(notebooks, key = { "n" + it.id }) { m ->
                        NotebookCard(
                            vm = vm,
                            meta = m,
                            onOpen = { vm.navigate(Screen.Editor(m.id)) },
                            onRename = { dialog = Dialog.RenameNotebook(m) },
                            onMove = { dialog = Dialog.MoveNotebook(m) },
                        )
                    }
                }
            }
        }
    }

    when (val d = dialog) {
        null -> Unit
        Dialog.NewNotebook -> NewNotebookDialog(
            defaultName = "Caderno ${library.notebooks.size + 1}",
            onDismiss = { dialog = null },
            onCreate = { name, pages -> dialog = null; vm.createNotebook(name, folderId, pages) },
        )
        Dialog.NewFolder -> TextInputDialog("Nova pasta", "Nova pasta", "Criar", onDismiss = { dialog = null }) {
            dialog = null
            vm.io { vm.repo.createFolder(it.trim(), folderId) }
        }
        is Dialog.RenameNotebook -> TextInputDialog("Renomear", d.meta.name, onDismiss = { dialog = null }) {
            dialog = null
            vm.io { vm.repo.renameNotebook(d.meta.id, it.trim()) }
        }
        is Dialog.RenameFolder -> TextInputDialog("Renomear pasta", d.folder.name, onDismiss = { dialog = null }) {
            dialog = null
            vm.io { vm.repo.renameFolder(d.folder.id, it.trim()) }
        }
        is Dialog.MoveNotebook -> MoveDialog(liveFolders, exclude = null, onDismiss = { dialog = null }) { target ->
            dialog = null
            vm.io { vm.repo.moveNotebook(d.meta.id, target) }
        }
        is Dialog.MoveFolder -> MoveDialog(liveFolders, exclude = d.folder.id, onDismiss = { dialog = null }) { target ->
            dialog = null
            vm.io { vm.repo.moveFolder(d.folder.id, target) }
        }
        is Dialog.NamePdf -> TextInputDialog("Nome do caderno", d.name, "Importar", onDismiss = { dialog = null }) {
            dialog = null
            vm.importPdf(d.uri, it.trim(), folderId)
        }
        is Dialog.TrashFolder -> ConfirmDialog(
            "Mover para a lixeira?",
            "A pasta \"${d.folder.name}\" e tudo dentro dela vão para a lixeira.",
            "Mover",
            onDismiss = { dialog = null },
        ) {
            dialog = null
            vm.io { vm.repo.trashFolder(d.folder.id) }
        }
        Dialog.Help -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Dicas") },
            text = {
                Text(
                    "• Toque em + para criar cadernos, pastas ou importar um PDF (planners).\n" +
                        "• No editor, use a caneta para escrever. Depois que a caneta é usada, o dedo passa a rolar e dar zoom.\n" +
                        "• Dois dedos: zoom e mover a página.\n" +
                        "• Deslize para trocar de página, ou use as setas ao lado do número.\n" +
                        "• O laço seleciona, move, redimensiona e apaga itens.\n" +
                        "• Configurações → Fontes: importe arquivos .ttf/.otf para usar no texto.",
                )
            },
            confirmButton = { TextButton(onClick = { dialog = null }) { Text("OK") } },
        )
    }
}

@Composable
private fun MoveDialog(folders: List<Folder>, exclude: String?, onDismiss: () -> Unit, onMove: (String?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover para…") },
        text = {
            Column {
                Text("Minhas Notas (início)", Modifier.fillMaxWidth().clickable { onMove(null) }.padding(12.dp))
                folders.filter { it.id != exclude }.sortedBy { it.name.lowercase() }.forEach { f ->
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth().clickable { onMove(f.id) }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Folder, null, tint = PenlyBlue)
                        Spacer(Modifier.width(8.dp))
                        Text(f.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

@Composable
private fun CardFooter(title: String, subtitle: String, bold: Boolean, menu: @Composable (close: () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 12.dp, start = 8.dp, end = 8.dp),
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
        Box {
            IconButton(onClick = { open = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, "Opções")
            }
            DropdownMenu(open, { open = false }) { menu { open = false } }
        }
    }
}

@Composable
private fun FolderCard(folder: Folder, onOpen: () -> Unit, onRename: () -> Unit, onMove: () -> Unit, onDelete: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.height(210.dp).fillMaxWidth().clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Folder, null, Modifier.size(170.dp), tint = PenlyBlue)
        }
        CardFooter(folder.name, formatDate(folder.updatedAt), bold = true) { close ->
            DropdownMenuItem(text = { Text("Abrir") }, onClick = { close(); onOpen() })
            DropdownMenuItem(text = { Text("Renomear") }, onClick = { close(); onRename() })
            DropdownMenuItem(text = { Text("Mover") }, onClick = { close(); onMove() })
            DropdownMenuItem(text = { Text("Excluir") }, onClick = { close(); onDelete() })
        }
    }
}

@Composable
private fun NotebookCard(vm: AppViewModel, meta: NotebookMeta, onOpen: () -> Unit, onRename: () -> Unit, onMove: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val thumb by produceState<ImageBitmap?>(null, meta.id, meta.updatedAt) {
        value = withContext(Dispatchers.IO) {
            val f = vm.repo.thumbFile(meta.id)
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() else null
        }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Box(Modifier.height(210.dp).fillMaxWidth().clickable(onClick = onOpen), contentAlignment = Alignment.Center) {
            val t = thumb
            if (t != null) {
                Image(
                    t, meta.name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.shadow(3.dp).background(Color.White),
                )
            } else {
                Box(Modifier.size(150.dp, 210.dp).shadow(3.dp).background(Color.White))
            }
        }
        CardFooter(meta.name, formatDate(meta.updatedAt), bold = false) { close ->
            DropdownMenuItem(text = { Text("Abrir") }, onClick = { close(); onOpen() })
            DropdownMenuItem(text = { Text("Renomear") }, onClick = { close(); onRename() })
            DropdownMenuItem(text = { Text("Duplicar") }, onClick = { close(); vm.io { vm.repo.duplicateNotebook(meta.id) } })
            DropdownMenuItem(text = { Text("Mover para pasta") }, onClick = { close(); onMove() })
            DropdownMenuItem(text = { Text("Exportar PDF") }, onClick = {
                close()
                scope.launch {
                    vm.busyMessage = "Gerando PDF…"
                    try {
                        val file = withContext(Dispatchers.IO) {
                            vm.repo.loadNotebook(meta.id)?.let { Exporter.exportPdf(context, it, vm.repo.assetsDir(meta.id)) }
                        }
                        if (file != null) Exporter.share(context, file, "application/pdf")
                    } catch (e: Exception) {
                        vm.toast = "Erro ao exportar: ${e.message}"
                    } finally {
                        vm.busyMessage = null
                    }
                }
            })
            DropdownMenuItem(text = { Text("Exportar arquivo .penlyapp (backup)") }, onClick = {
                close()
                scope.launch {
                    val file = withContext(Dispatchers.IO) {
                        vm.repo.loadNotebook(meta.id)?.let { Exporter.exportNotebookFile(context, vm.repo, it) }
                    }
                    if (file != null) Exporter.share(context, file, "application/zip")
                }
            })
            DropdownMenuItem(text = { Text("Excluir") }, onClick = { close(); vm.io { vm.repo.trashNotebook(meta.id) } })
        }
    }
}
