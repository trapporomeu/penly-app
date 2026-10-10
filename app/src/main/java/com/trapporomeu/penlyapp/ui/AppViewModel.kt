package com.trapporomeu.penlyapp.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trapporomeu.penlyapp.PenlyApp
import com.trapporomeu.penlyapp.model.BackupInfo
import com.trapporomeu.penlyapp.model.Page
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class Screen {
    data class Library(val folderId: String? = null) : Screen()
    data object Trash : Screen()
    data object Backups : Screen()
    data object Settings : Screen()
    data class Editor(val notebookId: String) : Screen()
}

enum class SortMode(val label: String) { UPDATED("Modificado recentemente"), NAME("Nome"), CREATED("Data de criação") }

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val penly = app as PenlyApp
    val repo = penly.repo
    val settings = penly.settings
    val library = repo.library

    val backStack = mutableStateListOf<Screen>(Screen.Library())
    val current: Screen get() = backStack.last()

    var sortMode by mutableStateOf(SortMode.UPDATED)
    var busyMessage by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    init {
        if (!settings.value.starterCreated) {
            settings.update { it.copy(starterCreated = true) }
            io { repo.createStarterPlanners() }
        }
    }

    fun navigate(s: Screen) {
        backStack.add(s)
    }

    /** Troca a tela do topo (usado pelo menu lateral). */
    fun switchRoot(s: Screen) {
        backStack.clear()
        backStack.add(s)
    }

    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        return true
    }

    fun io(block: suspend () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                block()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toast = "Erro: ${e.message ?: e.javaClass.simpleName}" }
            }
        }
    }

    fun createNotebook(name: String, folderId: String?, pages: List<Page>, open: Boolean = true) = io {
        val nb = repo.createNotebook(name, folderId, pages)
        if (open) withContext(Dispatchers.Main) { navigate(Screen.Editor(nb.id)) }
    }

    fun importPdf(uri: Uri, name: String, folderId: String?) = io {
        withContext(Dispatchers.Main) { busyMessage = "Importando PDF…" }
        try {
            val nb = repo.importPdf(uri, name, folderId) { i, n ->
                viewModelScope.launch(Dispatchers.Main) { busyMessage = "Importando PDF… página $i de $n" }
            }
            withContext(Dispatchers.Main) {
                if (nb == null) toast = "Não foi possível ler o PDF" else navigate(Screen.Editor(nb.id))
            }
        } finally {
            withContext(Dispatchers.Main) { busyMessage = null }
        }
    }

    fun importNotebookFile(uri: Uri, folderId: String?) = io {
        withContext(Dispatchers.Main) { busyMessage = "Importando caderno…" }
        try {
            val nb = getApplication<Application>().contentResolver.openInputStream(uri)?.use {
                repo.importNotebookFile(it, folderId)
            }
            withContext(Dispatchers.Main) { toast = if (nb == null) "Arquivo inválido" else "Caderno importado" }
        } finally {
            withContext(Dispatchers.Main) { busyMessage = null }
        }
    }

    fun restoreBackup(info: BackupInfo) = io {
        repo.restoreBackup(info)
        withContext(Dispatchers.Main) { toast = "Backup restaurado" }
    }
}
