@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.contacts.PickPhoneContract
import com.callstudio.app.domain.Countries
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.viewmodels.OutgoingPhase
import com.callstudio.app.ui.viewmodels.OutgoingViewModel

private const val MODE_FAKE = 0
private const val MODE_REAL = 1

@Composable
fun OutgoingScreen(nav: NavController, vm: OutgoingViewModel = viewModel()) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var showCountries by remember { mutableStateOf(false) }
    var showLink by remember { mutableStateOf(false) }
    var showConsent by rememberSaveable { mutableStateOf(false) }
    val ready = settings?.twilioReady == true
    var mode by rememberSaveable { mutableStateOf(MODE_FAKE) }

    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { vm.addClipFile(it) }
    }
    val contactPicker = rememberLauncherForActivityResult(PickPhoneContract()) { uri ->
        if (uri != null) vm.pickContact(uri)
    }

    Scaffold(
        topBar = {
            AppTopBar("Outgoing call") {
                if (mode == MODE_REAL) {
                    IconButton(onClick = { nav.navigate(Routes.OUTGOING_SETUP) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Outgoing setup")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = mode == MODE_FAKE, onClick = { mode = MODE_FAKE }, label = { Text("Fake call") })
                FilterChip(selected = mode == MODE_REAL, onClick = { mode = MODE_REAL }, label = { Text("Real call (Twilio)") })
            }
            if (mode == MODE_FAKE) {
                FakeOutgoingPane(nav)
            } else {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (!ready) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Set up outgoing calls first", style = MaterialTheme.typography.titleSmall)
                                Text("Real calls go out through your own Twilio account. Add its details once, then come back here.")
                                TextButton(onClick = { nav.navigate(Routes.OUTGOING_SETUP) }) { Text("Open setup") }
                            }
                        }
                    }

                    Text("Who to call", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { showCountries = true }, enabled = !ui.busy, modifier = Modifier.height(56.dp)) {
                            Text("${Countries.flag(ui.iso)} ${Countries.dialLabelOf(ui.iso)}")
                        }
                        OutlinedTextField(
                            value = ui.number, onValueChange = vm::setNumber, label = { Text("Phone number") },
                            singleLine = true, enabled = !ui.busy,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    OutlinedButton(onClick = { contactPicker.launch(Unit) }, enabled = !ui.busy, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Contacts, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Choose from contacts")
                    }

                    Text("Clips (played in this order)", style = MaterialTheme.typography.titleSmall)
                    if (ui.clips.isEmpty()) {
                        Text(
                            "No clips yet. Add one or more audio files or links. They play one after another once the person answers.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    ui.clips.forEachIndexed { index, clip ->
                        Card(Modifier.fillMaxWidth()) {
                            ListItem(
                                headlineContent = { Text("${index + 1}. ${clip.label}", maxLines = 1) },
                                supportingContent = {
                                    Text(if (clip.url != null && clip.uri == null) "Link" else if (clip.url != null) "Uploaded" else "Uploads when you call")
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { vm.moveClip(clip.id, -1) }, enabled = !ui.busy && index > 0) {
                                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up")
                                        }
                                        IconButton(onClick = { vm.moveClip(clip.id, 1) }, enabled = !ui.busy && index < ui.clips.lastIndex) {
                                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down")
                                        }
                                        IconButton(onClick = { vm.removeClip(clip.id) }, enabled = !ui.busy) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Remove clip")
                                        }
                                    }
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { clipPicker.launch(arrayOf("audio/*")) }, enabled = !ui.busy) { Text("Add audio files") }
                        OutlinedButton(onClick = { showLink = true }, enabled = !ui.busy) { Text("Add link") }
                    }

                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Join the call yourself afterwards") },
                            supportingContent = {
                                Text(
                                    "When the last clip ends, Twilio rings your own phone (set in the setup) and connects you to the person. " +
                                        "Off: the call ends after the last clip."
                                )
                            },
                            trailingContent = { Switch(checked = ui.connectLive, onCheckedChange = vm::setConnectLive, enabled = !ui.busy) },
                        )
                    }

                    ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                    when (ui.phase) {
                        OutgoingPhase.IDLE -> Button(
                            onClick = { showConsent = true }, enabled = ready, modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Call")
                        }
                        OutgoingPhase.FINISHED -> {
                            Text(ui.statusText ?: "Call ended", style = MaterialTheme.typography.titleMedium)
                            Button(onClick = { vm.reset() }, modifier = Modifier.fillMaxWidth()) { Text("New call") }
                        }
                        else -> {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                CircularProgressIndicator(Modifier.width(24.dp).height(24.dp), strokeWidth = 3.dp)
                                Text(ui.progressText ?: ui.statusText ?: "Working…", style = MaterialTheme.typography.titleMedium)
                            }
                            Button(
                                onClick = { vm.end() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.CallEnd, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(if (ui.phase == OutgoingPhase.ACTIVE) "End call" else "Cancel")
                            }
                        }
                    }

                    Text(
                        "This is a real call placed through your Twilio account from your Twilio number, not from your SIM, and " +
                            "Twilio's charges apply. Clips you upload are reachable by link. Only call people who have agreed to it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showCountries) {
        CountryPickerDialog(onPick = { vm.setCountry(it.iso); showCountries = false }, onDismiss = { showCountries = false })
    }
    if (showLink) {
        var link by remember { mutableStateOf("https://") }
        AlertDialog(
            onDismissRequest = { showLink = false },
            title = { Text("Add a clip link") },
            text = {
                OutlinedTextField(
                    value = link, onValueChange = { link = it }, singleLine = true,
                    label = { Text("Link to an MP3 or WAV file") }, modifier = Modifier.fillMaxWidth(),
                )
            },
            confirmButton = { TextButton(onClick = { vm.addClipLink(link); showLink = false }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { showLink = false }) { Text("Cancel") } },
        )
    }
    if (showConsent) {
        var agreed by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showConsent = false },
            title = { Text("Before you call") },
            text = {
                Row(verticalAlignment = Alignment.Top) {
                    Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                    Text(
                        "The person I'm calling has agreed to receive a call with recorded audio, and I have the right " +
                            "to play these clips.",
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = agreed, onClick = { showConsent = false; vm.start() }) { Text("Call now") }
            },
            dismissButton = { TextButton(onClick = { showConsent = false }) { Text("Cancel") } },
        )
    }
}
