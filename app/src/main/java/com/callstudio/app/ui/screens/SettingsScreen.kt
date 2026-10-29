@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.SectionTitle
import com.callstudio.app.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel()) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val timeouts = listOf(30, 45, 60)
    val rates = listOf(16_000 to "Voice · 16 kHz", 44_100 to "High · 44.1 kHz")

    Scaffold(topBar = { AppTopBar("Settings") }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SectionTitle("Appearance")
            NavRow("Call theme", st.theme.label) { nav.navigate(Routes.THEMES) }

            SectionTitle("Calls")
            NavRow("Audio and ringtone", "Ringtone, vibration, caller voice clip") { nav.navigate(Routes.AUDIO) }
            ListItem(
                headlineContent = { Text("Ring for") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        timeouts.forEachIndexed { i, s ->
                            SegmentedButton(
                                selected = st.ringTimeoutSeconds == s,
                                onClick = { vm.setRingTimeout(s) },
                                shape = SegmentedButtonDefaults.itemShape(i, timeouts.size),
                            ) { Text("$s s") }
                        }
                    }
                },
            )
            ListItem(
                headlineContent = { Text("Keep screen on during calls") },
                trailingContent = { Switch(checked = st.keepScreenOn, onCheckedChange = { vm.setKeepScreenOn(it) }) },
            )

            SectionTitle("Recording")
            ListItem(
                headlineContent = { Text("Recording quality") },
                supportingContent = {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        rates.forEachIndexed { i, (hz, label) ->
                            SegmentedButton(
                                selected = st.recordingSampleRate == hz,
                                onClick = { vm.setSampleRate(hz) },
                                shape = SegmentedButtonDefaults.itemShape(i, rates.size),
                            ) { Text(label, maxLines = 1) }
                        }
                    }
                },
            )
            NavRow("Recordings", "Play or delete saved recordings") { nav.navigate(Routes.RECORDINGS) }

            SectionTitle("History and setup")
            NavRow("Call history", "Answered, declined and missed calls") { nav.navigate(Routes.HISTORY) }
            NavRow("Permissions and setup", "Check what Android allows") { nav.navigate(Routes.SETUP) }

            SectionTitle("About")
            Text(
                "CallStudio creates a call-like experience on this phone only. It doesn't place real calls or contact anyone. " +
                    "Callers, schedules, recordings and history stay on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun NavRow(title: String, subtitle: String, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
