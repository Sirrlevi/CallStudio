@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.ui.call.CallerUi
import com.callstudio.app.ui.call.ControlSpec
import com.callstudio.app.ui.call.InCallModel
import com.callstudio.app.ui.call.InCallScreen
import com.callstudio.app.ui.call.IncomingActions
import com.callstudio.app.ui.call.IncomingCallScreen
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.theme.CallTheme
import com.callstudio.app.ui.viewmodels.CallersViewModel
import com.callstudio.app.ui.viewmodels.SettingsViewModel

@Composable
fun ThemeScreen(nav: NavController, vm: SettingsViewModel = viewModel(), callersVm: CallersViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val callers by callersVm.callers.collectAsStateWithLifecycle()
    var showInCall by rememberSaveable { mutableStateOf(false) }
    val first = callers?.firstOrNull()
    val sample = first?.let { CallerUi(it.name, it.phone, it.photoFile) } ?: CallerUi("Alex Morgan", "+1 555 0100", null)

    Scaffold(topBar = { AppTopBar("Call theme", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(selected = !showInCall, onClick = { showInCall = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Incoming") }
                SegmentedButton(selected = showInCall, onClick = { showInCall = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("In call") }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(CallTheme.entries.toList(), key = { it.name }) { theme ->
                    val selected = settings.theme == theme
                    Card(
                        onClick = { vm.setTheme(theme) },
                        border = if (selected) BorderStroke(3.dp, MaterialTheme.colorScheme.primary) else null,
                        colors = CardDefaults.cardColors(),
                    ) {
                        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            ScaledPreview(width = 140.dp, onClick = { vm.setTheme(theme) }) {
                                if (showInCall) {
                                    InCallScreen(
                                        theme,
                                        InCallModel(
                                            sample, 83_000L, null,
                                            listOf(
                                                ControlSpec("mute", "Mute", Icons.Filled.Mic, onClick = {}),
                                                ControlSpec("speaker", "Speaker", Icons.Filled.VolumeUp, active = true, onClick = {}),
                                                ControlSpec("record", "Record", Icons.Filled.FiberManualRecord, onClick = {}),
                                            ),
                                            onEnd = {},
                                        ),
                                    )
                                } else {
                                    IncomingCallScreen(theme, sample, IncomingActions({}, {}))
                                }
                            }
                            Text(theme.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                if (selected) "Selected" else theme.blurb,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Renders a real call screen at phone size, scaled down and inert, so thumbnails always match the actual layouts. */
@Composable
private fun ScaledPreview(width: Dp, onClick: () -> Unit, content: @Composable () -> Unit) {
    val scale = width.value / 360f
    Box(Modifier.size(width, 640.dp * scale).clip(RoundedCornerShape(14.dp))) {
        Box(
            Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)
                .size(360.dp, 640.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .consumeWindowInsets(WindowInsets.safeDrawing),
        ) { content() }
        // Covers the thumbnail so its buttons can't be operated; tapping it selects the theme.
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}
