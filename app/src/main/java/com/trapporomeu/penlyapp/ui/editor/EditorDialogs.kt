package com.trapporomeu.penlyapp.ui.editor

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.trapporomeu.penlyapp.data.NotebookRepository
import com.trapporomeu.penlyapp.model.Page
import com.trapporomeu.penlyapp.model.PageTemplate
import com.trapporomeu.penlyapp.render.PageRenderer
import com.trapporomeu.penlyapp.ui.library.TemplateLabels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

val StarterStickers = listOf(
    "⭐", "🌟", "❤️", "💙", "💚", "💛", "💜", "🧡", "✅", "❌", "❗", "❓",
    "📌", "📍", "📎", "📚", "📖", "✏️", "🖊️", "📝", "📅", "⏰", "💡", "🎯",
    "🏆", "🎉", "🎂", "🎁", "☀️", "🌙", "☁️", "🌧️", "🌈", "❄️", "🌸", "🌻",
    "🍀", "🍎", "☕", "🍕", "🐱", "🐶", "🦋", "✈️", "🏠", "💪", "😀", "😍",
    "😂", "😎", "🤔", "😴", "😢", "🥳", "👍", "👏", "🙏", "✨", "🔥", "💯",
)

@Composable
fun StickerDialog(repo: NotebookRepository, onDismiss: () -> Unit, onEmoji: (String) -> Unit, onImage: (File) -> Unit) {
    var version by remember { mutableIntStateOf(0) }
    var deleteMode by remember { mutableStateOf(false) }
    val userStickers = remember(version) { repo.stickersDir.listFiles()?.sortedBy { it.name } ?: emptyList() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            repo.addSticker(uri)
            version++
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Stickers") },
        text = {
            LazyVerticalGrid(GridCells.Adaptive(64.dp), Modifier.height(420.dp)) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Meus stickers", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        if (userStickers.isNotEmpty()) {
                            TextButton(onClick = { deleteMode = !deleteMode }) { Text(if (deleteMode) "Pronto" else "Remover") }
                        }
                        TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                            Text("+ Adicionar imagem")
                        }
                    }
                }
                items(userStickers, key = { it.name }) { f ->
                    val bmp by produceState<ImageBitmap?>(null, f) {
                        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }
                    }
                    Box(
                        Modifier.size(64.dp).padding(4.dp).clickable {
                            if (deleteMode) { f.delete(); version++ } else onImage(f)
                        },
                        contentAlignment = Alignment.Center,
                    ) {
                        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
                        if (deleteMode) Icon(Icons.Default.Close, "Remover", tint = Color.Red)
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Emojis", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
                items(StarterStickers) { e ->
                    Box(Modifier.size(56.dp).clickable { onEmoji(e) }, contentAlignment = Alignment.Center) {
                        Text(e, fontSize = 32.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fechar") } },
    )
}

@Composable
private fun PageThumb(page: Page, assets: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, System.identityHashCode(page)) {
        value = withContext(Dispatchers.Default) {
            PageRenderer.renderBitmap(context, page, assets, 220f / page.width).asImageBitmap()
        }
    }
    Box(
        modifier.aspectRatio(page.width / page.height).shadow(2.dp).background(Color.White),
    ) {
        bmp?.let { Image(it, null, Modifier.fillMaxSize()) }
    }
}

@Composable
fun PagesDialog(st: EditorState, onDismiss: () -> Unit) {
    val nb = st.notebook ?: return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().padding(24.dp), shape = MaterialTheme.shapes.large) {
            Column {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Páginas (${nb.pages.size})", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp).weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Fechar") }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    itemsIndexed(nb.pages, key = { _, p -> p.id }) { i, p ->
                        var menu by remember { mutableStateOf(false) }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                Modifier.height(190.dp).fillMaxWidth()
                                    .border(if (i == st.pageIndex) 3.dp else 0.dp, if (i == st.pageIndex) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { st.goTo(i); onDismiss() },
                                contentAlignment = Alignment.Center,
                            ) {
                                PageThumb(p, st.assets)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", textAlign = TextAlign.Center)
                                Box {
                                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Opções") }
                                    DropdownMenu(menu, { menu = false }) {
                                        DropdownMenuItem(text = { Text("Inserir página depois") }, onClick = {
                                            menu = false; st.goTo(i); st.addPageAfter()
                                        })
                                        DropdownMenuItem(text = { Text("Duplicar") }, onClick = { menu = false; st.duplicatePage(i) })
                                        if (i > 0) DropdownMenuItem(text = { Text("Mover para trás") }, onClick = { menu = false; st.movePage(i, i - 1) })
                                        if (i < nb.pages.lastIndex) DropdownMenuItem(text = { Text("Mover para frente") }, onClick = { menu = false; st.movePage(i, i + 1) })
                                        if (nb.pages.size > 1) DropdownMenuItem(text = { Text("Excluir") }, onClick = { menu = false; st.deletePage(i) })
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TemplateDialog(onDismiss: () -> Unit, onPick: (PageTemplate, Boolean) -> Unit) {
    var all by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modelo da página") },
        text = {
            Column {
                TemplateLabels.forEach { (t, label) ->
                    Text(label, Modifier.fillMaxWidth().clickable { onPick(t, all) }.padding(vertical = 12.dp, horizontal = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(all, { all = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Aplicar a todas as páginas (exceto capa)")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
