@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.callstudio.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.FakeOutgoingActivity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.fakeoutgoing.FakeOutcome
import com.callstudio.app.fakeoutgoing.FakePhase
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.viewmodels.FakeOutgoingViewModel
import com.callstudio.app.ui.viewmodels.OutgoingViewModel

@Composable
fun OutgoingScreen(
    nav: NavController,
    realVm: OutgoingViewModel = viewModel(),
    fakeVm: FakeOutgoingViewModel = viewModel(),
) {
    var mode by rememberSaveable { mutableStateOf(0) } // 0 fake, 1 real
    Scaffold(
        topBar = {
            AppTopBar("Outgoing call") {
                if (mode == 1) {
                    IconButton(onClick = { nav.navigate(Routes.OUTGOING_SETUP) }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Outgoing setup")
                    }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Fake call") }
                SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Real call") }
            }
            if (mode == 0) FakeOutgoingPanel(nav, fakeVm) else RealOutgoingBody(nav, realVm, PaddingValues(0.dp))
        }
    }
}

@Composable
private fun FakeOutgoingPanel(nav: NavController, vm: FakeOutgoingViewModel) {
    val ctx = LocalContext.current
    val ui by vm.ui.collectAsStateWithLifecycle()
    val callers by vm.callers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val call by vm.call.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }
    val clipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.attachClip(uri)
    }
    val list = callers

    when {
        list == null -> LoadingBox()
        list.isEmpty() -> EmptyState(
            Icons.Filled.Person, "Add a caller first", "Pick someone you saved, just like for incoming calls.",
            "Add caller",
        ) { nav.navigate(Routes.callerEdit(0)) }
        else -> {
            val caller = list.firstOrNull { it.id == ui.callerId }
                ?: list.firstOrNull { it.id == settings.lastCallerId }
                ?: list.first()
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (call.phase != FakePhase.IDLE) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("Outgoing call in progress", style = MaterialTheme.typography.titleSmall)
                                Text(call.name.ifBlank { call.number })
                            }
                            Button(onClick = { ctx.startActivity(FakeOutgoingActivity.intent(ctx)) }) { Text("Open") }
                        }
                    }
                }

                Text("Who to call", style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        CallerAvatar(caller.photoFile, caller.name, 56.dp)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(caller.name, style = MaterialTheme.typography.titleMedium)
                            if (caller.phone.isNotBlank()) Text(caller.displayNumber(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { picking = true }) { Text("Change") }
                    }
                }

                Text("Audio after they pick up (optional)", style = MaterialTheme.typography.titleSmall)
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            ui.clipLabel ?: "No audio. The call connects in silence.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { clipPicker.launch(arrayOf("audio/*")) }) {
                                Text(if (ui.clipUri == null) "Attach audio" else "Change audio")
                            }
                            if (ui.clipUri != null) TextButton(onClick = { vm.removeClip() }) { Text("Remove") }
                        }
                    }
                }

                Text("How it ends", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = ui.outcome == null, onClick = { vm.setOutcome(null) }, label = { Text("Random") })
                    FakeOutcome.entries.forEach { outcome ->
                        FilterChip(selected = ui.outcome == outcome, onClick = { vm.setOutcome(outcome) }, label = { Text(outcome.label) })
                    }
                }
                Text(
                    "Random mixes pick-ups, unanswered rings, busy, declined and network drops. Whatever happens, it rings " +
                        "for at least 20 seconds first.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                ui.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = { if (vm.start(caller)) ctx.startActivity(FakeOutgoingActivity.intent(ctx)) },
                    enabled = call.phase == FakePhase.IDLE,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Call, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Call")
                }
                Text(
                    "Staged on this phone only. No real call is made and nobody is contacted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (picking) {
                CallerPickDialog(
                    callers = list,
                    onPick = { vm.selectCaller(it.id); picking = false },
                    onAdd = { picking = false; nav.navigate(Routes.callerEdit(0)) },
                    onDismiss = { picking = false },
                )
            }
        }
    }
}
