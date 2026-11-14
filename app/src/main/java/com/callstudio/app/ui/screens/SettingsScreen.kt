@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.clickable
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.callstudio.app.container
import com.callstudio.app.domain.CallTimerOffset
import com.callstudio.app.update.UpdateUiState
import com.callstudio.app.ui.components.DownloadProgress
import com.callstudio.app.ui.components.describeBuild
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
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
import com.callstudio.app.preferences.EdgeGlowStyle

@Composable
fun SettingsScreen(nav: NavController, vm: SettingsViewModel = viewModel()) {
    val st by vm.settings.collectAsStateWithLifecycle()
    var timerDialog by remember { mutableStateOf(false) }
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
            NavRow(
                "Ongoing call timer",
                if (st.callTimerOffsetSeconds == 0L) "Normal timer. Tap to set the timer to any time, even while a call is running."
                else "Set to ${CallTimerOffset.label(st.callTimerOffsetSeconds)}: a running call and every new call. Tap to change.",
            ) { timerDialog = true }
            ListItem(
                headlineContent = { Text("Neon edge glow") },
                supportingContent = {
                    Column {
                        Text("Coloured light that runs around the screen edge while a call rings.")
                        val glowChoices = EdgeGlowStyle.entries
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            glowChoices.forEachIndexed { index, style ->
                                SegmentedButton(
                                    selected = st.edgeGlow == style,
                                    onClick = { vm.setEdgeGlow(style) },
                                    shape = SegmentedButtonDefaults.itemShape(index, glowChoices.size),
                                ) { Text(style.label, maxLines = 1, softWrap = false, style = MaterialTheme.typography.labelMedium) }
                            }
                        }
                    }
                },
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

            SectionTitle("App updates")
            UpdateAccessSettings()

            SectionTitle("About")
            Text(
                "Phone can simulate scheduled incoming calls. Outgoing calls use the Twilio account configured in the Outgoing setup. " +
                    "Caller profiles, schedules, recordings and history are stored on this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
    if (timerDialog) {
        CallTimerDialog(
            currentSeconds = st.callTimerOffsetSeconds,
            onSave = { vm.setCallTimerOffset(it); timerDialog = false },
            onDismiss = { timerDialog = false },
        )
    }
}

@Composable
private fun CallTimerDialog(currentSeconds: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    var hours by remember { mutableStateOf(CallTimerOffset.hours(currentSeconds).toString()) }
    var minutes by remember { mutableStateOf(CallTimerOffset.minutes(currentSeconds).toString()) }
    var seconds by remember { mutableStateOf(CallTimerOffset.secondsPart(currentSeconds).toString()) }
    val total = CallTimerOffset.fromParts(hours.toLongOrNull() ?: 0L, minutes.toLongOrNull() ?: 0L, seconds.toLongOrNull() ?: 0L)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ongoing call timer") },
        text = {
            Column {
                Text(
                    "The timer of a call that is running right now, and of every new call, jumps to this time and keeps running. " +
                        "For incoming and outgoing calls, in the notification and on the call screens.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimerField("Hours", hours, 4, Modifier.weight(1.3f)) { hours = it }
                    TimerField("Min", minutes, 2, Modifier.weight(1f)) { minutes = it }
                    TimerField("Sec", seconds, 2, Modifier.weight(1f)) { seconds = it }
                }
                Text(
                    "Starts at ${CallTimerOffset.label(total)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(total) }) { Text("Save") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onSave(0L) }) { Text("Normal timer") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun TimerField(label: String, value: String, maxDigits: Int, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { ch -> ch.isDigit() }.take(maxDigits)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
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

@Composable
private fun UpdateAccessSettings() {
    val context = LocalContext.current
    val updates = remember { context.container.updates }
    val state by updates.state.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(updates.hasToken()) }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            "Installed: ${describeBuild(updates.installedVersionName, updates.installedVersionCode)}",
            style = MaterialTheme.typography.bodyMedium,
        )
        val status = when (val s = state) {
            UpdateUiState.Checking -> "Checking for updates…"
            UpdateUiState.UpToDate -> "You have the latest version."
            is UpdateUiState.CheckFailed -> s.message
            is UpdateUiState.Available -> "Update available: ${s.info.versionName}"
            is UpdateUiState.Downloading -> "Downloading ${s.info.versionName}…"
            is UpdateUiState.ReadyToInstall -> "Ready to install: ${s.info.versionName}"
            UpdateUiState.NotConfigured -> "Add the token below to turn updates on."
            UpdateUiState.Idle -> ""
        }
        if (status.isNotBlank()) {
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (state is UpdateUiState.CheckFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val s = state
        when (s) {
            is UpdateUiState.Available -> {
                if (s.info.notes.isNotBlank()) Text(s.info.notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                s.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { updates.downloadAndInstall() }, modifier = Modifier.padding(top = 8.dp)) { Text("Download and install") }
            }
            is UpdateUiState.Downloading -> DownloadProgress(s.downloaded, s.total)
            is UpdateUiState.ReadyToInstall -> {
                s.message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                Button(onClick = { updates.downloadAndInstall() }, modifier = Modifier.padding(top = 8.dp)) { Text("Install now") }
            }
            else -> if (saved) {
                OutlinedButton(
                    onClick = { updates.check() },
                    enabled = s != UpdateUiState.Checking,
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Check for updates") }
            }
        }
        Text(
            "Updates install over the current app, so your callers, schedules, settings and permissions stay. No uninstall needed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(12.dp))
        Text(
            "Phone is stored in a private GitHub repository. Add a read-only token once so the app can check and download its updates automatically.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("GitHub fine-grained token") },
            placeholder = { Text("github_pat_…") },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                runCatching {
                    updates.saveToken(token)
                    token = ""
                    saved = true
                    message = "Token saved securely. Checking for updates…"
                }.onFailure {
                    message = it.message ?: "Could not save the token."
                }
            },
            enabled = token.isNotBlank(),
        ) {
            Text("Save token and check")
        }
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/settings/personal-access-tokens/new"))
                    )
                }
            },
        ) {
            Text("Create a fine-grained token")
        }
        if (saved) {
            Text(
                "Update access is saved on this phone. The token is encrypted with Android Keystore.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = {
                updates.clearToken()
                saved = false
                message = "Saved update token removed."
            }) { Text("Remove saved token") }
        }
        if (message.isNotBlank()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "When creating the token, select only Sirrlevi/CallStudio and grant Contents: Read-only. Do not share this token.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
