@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.data.RecordingEntity
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.ConfirmDialog
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.formatWhen
import com.callstudio.app.ui.statusLabel
import com.callstudio.app.ui.viewmodels.LibraryViewModel
import java.util.Locale

@Composable
fun RecordingsScreen(nav: NavController, vm: LibraryViewModel = viewModel()) {
    val recordings by vm.recordings.collectAsStateWithLifecycle()
    val playerState by vm.playerState.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<RecordingEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val list = recordings

    DisposableEffect(Unit) { onDispose { vm.stopPlayback() } }
    LaunchedEffect(vm.notice) {
        vm.notice?.let {
            snackbar.showSnackbar(it)
            vm.clearNotice()
        }
    }

    Scaffold(
        topBar = { AppTopBar("Recordings", onBack = { nav.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(
                    Icons.Filled.Mic, "No recordings yet",
                    "During a call, tap Record to save what your microphone hears. Nothing records unless you start it.",
                )
                else -> LazyColumn {
                    items(list, key = { it.id }) { r ->
                        val playing = vm.playingId == r.id && playerState == VoiceState.Playing
                        ListItem(
                            headlineContent = { Text(r.callerName.ifBlank { "Recording" }) },
                            supportingContent = {
                                val size = String.format(Locale.getDefault(), "%.1f MB", r.sizeBytes / 1_048_576.0)
                                Text("${formatWhen(r.createdAt)} · ${TimeFormat.elapsed(r.durationMs)} · $size · microphone only")
                            },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { vm.toggle(r) }, enabled = vm.fileExists(r)) {
                                        Icon(if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = if (playing) "Stop" else "Play")
                                    }
                                    IconButton(onClick = { deleting = r }) { Icon(Icons.Filled.Delete, contentDescription = "Delete recording") }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    deleting?.let { r ->
        ConfirmDialog(
            title = "Delete this recording?",
            text = "The audio file will be permanently removed from your phone.",
            confirmLabel = "Delete",
            onConfirm = { vm.delete(r); deleting = null },
            onDismiss = { deleting = null },
        )
    }
}

@Composable
fun HistoryScreen(nav: NavController, vm: LibraryViewModel = viewModel()) {
    val history by vm.history.collectAsStateWithLifecycle()
    var confirmClear by remember { mutableStateOf(false) }
    val list = history

    Scaffold(
        topBar = {
            AppTopBar("Call history", onBack = { nav.popBackStack() }) {
                if (!list.isNullOrEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(Icons.Filled.History, "No calls yet", "Calls you answer, decline or miss will be listed here.")
                else -> LazyColumn {
                    items(list, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(e.callerName.ifBlank { e.callerPhone }) },
                            supportingContent = { Text("${e.statusLabel()} · ${formatWhen(e.endedAtMillis ?: e.triggerAtMillis)}") },
                            leadingContent = { CallerAvatar(e.callerPhotoFile, e.callerName, 40.dp) },
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear call history?",
            text = "This removes the list of past calls. Recordings are kept.",
            confirmLabel = "Clear",
            onConfirm = { vm.clearHistory(); confirmClear = false },
            onDismiss = { confirmClear = false },
        )
    }
}
