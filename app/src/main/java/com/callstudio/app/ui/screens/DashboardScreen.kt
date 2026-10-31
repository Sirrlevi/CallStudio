@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.callstudio.app.CallActivity
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.ScheduleSpec
import com.callstudio.app.scheduling.ScheduleCoordinator
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.components.OnResume
import com.callstudio.app.ui.formatWhen
import com.callstudio.app.ui.message
import com.callstudio.app.ui.viewmodels.ScheduleViewModel
import kotlinx.coroutines.launch

@Composable
fun DashboardScreen(nav: NavController, vm: ScheduleViewModel = viewModel()) {
    val ctx = LocalContext.current
    val callers by vm.callers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val session by vm.session.collectAsStateWithLifecycle()
    var gaps by remember { mutableStateOf(vm.setupGaps()) }
    OnResume { gaps = vm.setupGaps() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val list = callers

    Scaffold(
        topBar = { AppTopBar("CallStudio") },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        when {
            list == null -> Box(Modifier.padding(pad)) { LoadingBox() }
            list.isEmpty() -> Box(Modifier.padding(pad)) {
                EmptyState(
                    Icons.Filled.Person, "Add your first caller",
                    "Create a caller, then start or schedule a call.",
                    "Add caller",
                ) { nav.navigate(Routes.callerEdit(0)) }
            }
            else -> {
                val selected: CallerEntity = list.firstOrNull { it.id == settings.lastCallerId } ?: list.first()

                fun startNow() {
                    scope.launch {
                        val id = vm.startNow(selected.id)
                        if (id != null) ctx.startActivity(CallActivity.intent(ctx, id))
                        else snackbar.showSnackbar("Couldn't start the call.")
                    }
                }

                fun quick(spec: ScheduleSpec) {
                    scope.launch {
                        val msg = when (val o = vm.schedule(selected, spec, null)) {
                            is ScheduleCoordinator.Outcome.Scheduled -> "Call set for ${formatWhen(o.event.triggerAtMillis)}"
                            is ScheduleCoordinator.Outcome.Rejected -> o.problem.message()
                        }
                        snackbar.showSnackbar(msg)
                    }
                }

                Column(
                    Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (session.isLive) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(if (session.status?.name == "RINGING") "Incoming call" else "Call in progress", style = MaterialTheme.typography.titleSmall)
                                    Text(session.displayName)
                                }
                                Button(onClick = { ctx.startActivity(CallActivity.intent(ctx, session.eventId)) }) { Text("Open") }
                            }
                        }
                    }
                    if (gaps.isNotEmpty()) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("Finish setup", style = MaterialTheme.typography.titleSmall)
                                Text(gaps.joinToString(". ") + ".", style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { nav.navigate(Routes.SETUP) }) { Text("Open setup") }
                            }
                        }
                    }
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CallerAvatar(selected.photoFile, selected.name, 56.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(selected.name, style = MaterialTheme.typography.titleMedium)
                                if (selected.phone.isNotBlank()) Text(selected.displayNumber(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { picking = true }) { Text("Change") }
                        }
                    }
                    Button(onClick = { startNow() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Call now")
                    }
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.OUTGOING) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Call, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Outgoing SIM call")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.schedule(selected.id, mode = 1)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Alarm, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Call in…")
                        }
                        OutlinedButton(
                            onClick = { nav.navigate(Routes.schedule(selected.id, mode = 3)) },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(Icons.Filled.Schedule, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Pick a time")
                        }
                    }
                    Text("Quick delay", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = { quick(ScheduleSpec.AfterSeconds(10)) }, label = { Text("10 s") })
                        AssistChip(onClick = { quick(ScheduleSpec.AfterSeconds(30)) }, label = { Text("30 s") })
                        AssistChip(onClick = { quick(ScheduleSpec.AfterMinutes(1)) }, label = { Text("1 min") })
                        AssistChip(onClick = { quick(ScheduleSpec.AfterMinutes(5)) }, label = { Text("5 min") })
                        AssistChip(onClick = { quick(ScheduleSpec.AfterMinutes(15)) }, label = { Text("15 min") })
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.preview(selected.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Preview the call screen")
                    }
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
}
