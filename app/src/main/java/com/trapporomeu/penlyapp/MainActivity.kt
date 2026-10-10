package com.trapporomeu.penlyapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.trapporomeu.penlyapp.ui.AppViewModel
import com.trapporomeu.penlyapp.ui.Screen
import com.trapporomeu.penlyapp.ui.editor.EditorScreen
import com.trapporomeu.penlyapp.ui.library.BackupsScreen
import com.trapporomeu.penlyapp.ui.library.LibraryScreen
import com.trapporomeu.penlyapp.ui.library.TrashScreen
import com.trapporomeu.penlyapp.ui.settings.SettingsScreen
import com.trapporomeu.penlyapp.ui.theme.PenlyTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings by vm.settings.state.collectAsStateWithLifecycle()
            PenlyTheme(settings.theme, settings.uiSize) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot(vm)
                }
            }
        }
    }
}

@Composable
private fun AppRoot(vm: AppViewModel) {
    val context = LocalContext.current
    BackHandler(enabled = vm.backStack.size > 1) { vm.back() }

    when (val s = vm.current) {
        is Screen.Library -> LibraryScreen(vm, s.folderId)
        Screen.Trash -> TrashScreen(vm)
        Screen.Backups -> BackupsScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
        is Screen.Editor -> EditorScreen(vm, s.notebookId)
    }

    vm.busyMessage?.let { msg ->
        Box(
            Modifier.fillMaxSize().background(Color(0x88000000)),
            contentAlignment = Alignment.Center,
        ) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(msg, Modifier.padding(top = 16.dp))
                }
            }
        }
    }

    LaunchedEffect(vm.toast) {
        vm.toast?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.toast = null
        }
    }
}
