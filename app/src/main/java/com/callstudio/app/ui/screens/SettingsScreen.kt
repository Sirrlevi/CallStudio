@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.clickable
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.callstudio.app.MainActivity
import com.callstudio.app.update.GitHubTokenStore
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
import com.callstudio.app.preferences.NotificationBadgeStyle

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

            SectionTitle("Notifications")
            ListItem(
                headlineContent = { Text("Caller photo badge") },
                supportingContent = {
                    Column {
                        Text("Choose the small badge shown on the caller photo in call notifications.")
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            val choices = listOf(
                                NotificationBadgeStyle.ONLINE_DOT to "Green dot",
                                NotificationBadgeStyle.APP_LOGO to "Phone logo",
                            )
                            choices.forEachIndexed { i, (style, label) ->
                                SegmentedButton(
                                    selected = st.notificationBadgeStyle == style,
                                    onClick = { vm.setNotificationBadgeStyle(style) },
                                    shape = SegmentedButtonDefaults.itemShape(i, choices.size),
                                ) { Text(label, maxLines = 1) }
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
                "Phone can simulate scheduled incoming calls and can also place outgoing cellular calls through your SIM. " +
                    "Audio clips selected for outgoing calls play on this device only; Android does not expose direct SIM-call audio injection.",
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

@Composable
private fun UpdateAccessSettings() {
    val context = LocalContext.current
    val tokenStore = remember { GitHubTokenStore(context) }
    var token by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(tokenStore.hasToken()) }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
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
                    tokenStore.saveToken(token)
                    token = ""
                    saved = true
                    message = "Token saved securely. Checking for updates…"
                    (context as? MainActivity)?.checkForUpdatesNow()
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
                tokenStore.clear()
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
