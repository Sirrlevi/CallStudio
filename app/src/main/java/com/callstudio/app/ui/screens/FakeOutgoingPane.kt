@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.FakeOutgoingActivity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.Countries
import com.callstudio.app.fakeout.FakeOutcome
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.viewmodels.FakeOutgoingViewModel

/**
 * The Fake call mode of the Outgoing tab: pick a contact (as for an incoming call) or type a number, optionally
 * attach an audio file, tap Call. Everything stays on this phone.
 */
@Composable
fun FakeOutgoingPane(nav: NavController, vm: FakeOutgoingViewModel = viewModel()) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val callers by vm.callers.collectAsStateWithLifecycle()
    val call by vm.callState.collectAsStateWithLifecycle()
    var showPicker by remember { mutableStateOf(false) }
    var showCountries by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.acceptClip(uri)
    }
    val chosen = callers?.firstOrNull { it.id == ui.callerId }
    val s = ui.settings

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (call.isLive) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Call in progress", style = MaterialTheme.typography.titleSmall)
                        Text(call.request?.title.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = { ctx.startActivity(FakeOutgoingActivity.intent(ctx)) }) { Text("Return to call") }
                }
            }
        }

        Text("Who to call", style = MaterialTheme.typography.titleSmall)
        if (chosen != null) {
            Card(Modifier.fillMaxWidth().clickable { showPicker = true }) {
                ListItem(
                    leadingContent = { CallerAvatar(chosen.photoFile, chosen.name, 44.dp) },
                    headlineContent = { Text(chosen.name, maxLines = 1) },
                    supportingContent = { Text(chosen.displayNumber()) },
                    trailingContent = {
                        IconButton(onClick = { vm.clearCaller() }) { Icon(Icons.Filled.Close, contentDescription = "Remove contact") }
                    },
                )
            }
        } else {
            OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Contacts, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Choose a contact")
            }
            Text("or dial a number", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { showCountries = true }, modifier = Modifier.height(56.dp)) {
                    Text("${Countries.flag(ui.iso)} ${Countries.dialLabelOf(ui.iso)}")
                }
                OutlinedTextField(
                    value = ui.number, onValueChange = vm::setNumber, label = { Text("Phone number") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Text("Audio after pickup (optional)", style = MaterialTheme.typography.titleSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.voiceLabel != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                        Text(ui.voiceLabel.orEmpty(), maxLines = 1, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text(
                        "No file. Add one and it starts playing by itself a moment after the other side picks up.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { clipPicker.launch(arrayOf("audio/*")) }) {
                        Text(if (ui.voiceUri == null) "Add audio file" else "Change file")
                    }
                    if (ui.voiceUri != null) TextButton(onClick = { vm.removeClip() }) { Text("Remove") }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column {
                ListItem(
                    headlineContent = { Text("Call settings") },
                    supportingContent = { Text("SIM label, tones, voice messages, test endings") },
                    modifier = Modifier.clickable { showSettings = !showSettings },
                )
                if (showSettings) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("SIM shown on the call screen", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(1 to "SIM 1", 2 to "SIM 2", 0 to "Hidden").forEach { (slot, label) ->
                                FilterChip(
                                    selected = s.simSlot == slot,
                                    onClick = { vm.updateSettings { it.copy(simSlot = slot) } },
                                    label = { Text(label) },
                                )
                            }
                        }
                    }
                    SettingSwitch(
                        "Dialing tones", "Key tones of the number while it says Calling…",
                        s.dialTones,
                    ) { v -> vm.updateSettings { it.copy(dialTones = v) } }
                    SettingSwitch(
                        "Spoken network messages", "Busy and no-answer calls sometimes end with a voice message",
                        s.announcements,
                    ) { v -> vm.updateSettings { it.copy(announcements = v) } }
                    SettingSwitch(
                        "Vibrate when answered", "One short buzz when the other side picks up",
                        s.vibrateOnAnswer,
                    ) { v -> vm.updateSettings { it.copy(vibrateOnAnswer = v) } }
                    SettingSwitch(
                        "Busy or drop before 20 s", "Off: every call rings at least 20 seconds. On: busy and network drops can come sooner",
                        s.earlyBusyAndDrop,
                    ) { v -> vm.updateSettings { it.copy(earlyBusyAndDrop = v) } }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("How the call ends", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Random is the default and picks a new ending every call. Fix one only to test.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = s.forced == null,
                                onClick = { vm.updateSettings { it.copy(forced = null) } },
                                label = { Text("Random") },
                            )
                            FakeOutcome.entries.forEach { o ->
                                FilterChip(
                                    selected = s.forced == o,
                                    onClick = { vm.updateSettings { it.copy(forced = o) } },
                                    label = { Text(o.label) },
                                )
                            }
                        }
                    }
                }
            }
        }

        ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Button(
            onClick = { vm.start { ctx.startActivity(FakeOutgoingActivity.intent(ctx)) } },
            enabled = ui.canCall && !call.isLive,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Call, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Call")
        }

        Text(
            "A fake call on this phone only. Nothing is dialed, no one is contacted and no audio leaves the device. " +
                "It rings for 20 to 45 seconds and then answers, rings out, drops, is busy or is rejected, at random.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showPicker && callers != null) {
        CallerPickDialog(
            callers = callers.orEmpty(),
            onPick = { vm.selectCaller(it.id); showPicker = false },
            onAdd = { showPicker = false; nav.navigate(Routes.callerEdit(0)) },
            onDismiss = { showPicker = false },
        )
    }
    if (showCountries) {
        CountryPickerDialog(onPick = { vm.setCountry(it.iso); showCountries = false }, onDismiss = { showCountries = false })
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}
