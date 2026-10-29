@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.audio.PickRingtoneContract
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.preferences.RingtoneSource
import com.callstudio.app.preferences.VoicePlayMode
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.SectionTitle
import com.callstudio.app.ui.viewmodels.SettingsViewModel

@Composable
fun AudioSettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel()) {
    val st by vm.settings.collectAsStateWithLifecycle()
    val voiceState by vm.voicePreviewState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            vm.stopRingPreview()
            vm.stopVoicePreview()
        }
    }

    val systemPicker = rememberLauncherForActivityResult(PickRingtoneContract()) { uri: Uri? -> if (uri != null) vm.onSystemRingtonePicked(uri) }
    val ringFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.onCustomRingtonePicked(uri) }
    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> if (uri != null) vm.onVoicePicked(uri) }
    val audioTypes = arrayOf("audio/*")

    Scaffold(topBar = { AppTopBar("Audio and ringtone", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            SectionTitle("Ringtone")
            RadioRow(st.ringtoneSource == RingtoneSource.DEFAULT, "Phone's default ringtone", "Follows your current system ringtone") { vm.useDefaultRingtone() }
            RadioRow(
                st.ringtoneSource == RingtoneSource.SYSTEM, "System sound",
                if (st.ringtoneSource == RingtoneSource.SYSTEM) st.ringtoneLabel ?: "Chosen" else "Pick from your phone's sounds",
            ) { systemPicker.launch(st.ringtoneUri?.let { Uri.parse(it) }.takeIf { st.ringtoneSource == RingtoneSource.SYSTEM }) }
            RadioRow(
                st.ringtoneSource == RingtoneSource.CUSTOM, "Audio file",
                if (st.ringtoneSource == RingtoneSource.CUSTOM) st.ringtoneLabel ?: "Chosen" else "Choose a file from storage",
            ) { ringFilePicker.launch(audioTypes) }
            RadioRow(st.ringtoneSource == RingtoneSource.SILENT, "No sound", "Vibration only, if enabled") { vm.useSilentRingtone() }

            ListItem(
                headlineContent = { Text("Vibration") },
                supportingContent = { Text("Vibrate while the call is ringing") },
                trailingContent = { Switch(checked = st.vibrate, onCheckedChange = { vm.setVibrate(it) }) },
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { vm.toggleRingPreview() }) {
                    Icon(if (vm.ringPreviewing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (vm.ringPreviewing) "Stop preview" else "Preview ringtone")
                }
            }
            vm.notice?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 8.dp))
            }

            SectionTitle("Caller voice clip")
            ListItem(
                headlineContent = { Text(st.voiceLabel ?: "No clip selected") },
                supportingContent = { Text("Plays on this device after you answer. MP3, M4A, WAV, OGG and FLAC usually work.") },
            )
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { voicePicker.launch(audioTypes) }) { Text(if (st.voiceUri == null) "Choose audio file" else "Change file") }
                if (st.voiceUri != null) {
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { vm.toggleVoicePreview() }) {
                        Icon(if (voiceState == VoiceState.Playing) Icons.Filled.Stop else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (voiceState == VoiceState.Playing) "Pause" else "Test")
                    }
                    TextButton(onClick = { vm.clearVoice() }) { Text("Remove") }
                }
            }
            (voiceState as? VoiceState.Error)?.let {
                Text(it.message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp, 8.dp))
            }
            RadioRow(st.voiceMode == VoicePlayMode.AUTO_ONCE, "Play once after answering", null) { vm.setVoiceMode(VoicePlayMode.AUTO_ONCE) }
            RadioRow(st.voiceMode == VoicePlayMode.AUTO_REPEAT, "Repeat until the call ends", null) { vm.setVoiceMode(VoicePlayMode.AUTO_REPEAT) }
            RadioRow(st.voiceMode == VoicePlayMode.MANUAL, "Only when I tap Play", "You can replay it any time during the call") { vm.setVoiceMode(VoicePlayMode.MANUAL) }

            SectionTitle("Output")
            ListItem(
                headlineContent = { Text("Start calls on speaker") },
                supportingContent = { Text("Otherwise audio starts from the earpiece, like a normal call") },
                trailingContent = { Switch(checked = st.startOnSpeaker, onCheckedChange = { vm.setStartOnSpeaker(it) }) },
            )
            Text(
                "Sounds play on this device only. CallStudio doesn't place real calls or send audio to anyone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}

@Composable
private fun RadioRow(selected: Boolean, title: String, subtitle: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = if (subtitle != null) ({ Text(subtitle) }) else null,
        leadingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}
