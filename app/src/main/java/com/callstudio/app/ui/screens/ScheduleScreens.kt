@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.callstudio.app.ui.screens

import android.net.Uri
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.callstudio.app.CallActivity
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.ScheduleCalculator
import com.callstudio.app.domain.ScheduleResult
import com.callstudio.app.domain.ScheduleSpec
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.scheduling.ScheduleCoordinator
import com.callstudio.app.ui.Routes
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.ConfirmDialog
import com.callstudio.app.ui.components.EmptyState
import com.callstudio.app.ui.components.LoadingBox
import com.callstudio.app.ui.components.OnResume
import com.callstudio.app.ui.formatWhen
import com.callstudio.app.ui.message
import com.callstudio.app.ui.viewmodels.ScheduleViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val modeLabels = listOf("Now", "Seconds", "Minutes", "Time")
private val secondPresets = listOf(10, 30, 45, 60)
private val minutePresets = listOf(1, 5, 10, 15, 30, 60)

@Composable
fun ScheduleScreen(
    nav: NavController,
    callerId: Long,
    eventId: Long,
    initialMode: Int,
    vm: ScheduleViewModel = viewModel(),
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val callers by vm.callers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val editing = eventId > 0

    var mode by rememberSaveable { mutableStateOf(if (editing) 3 else initialMode.coerceIn(0, 3)) }
    var amount by rememberSaveable { mutableStateOf("30") }
    var date by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var time by rememberSaveable {
        mutableStateOf(LocalTime.now().plusMinutes(10).withSecond(0).withNano(0).toString())
    }
    var chosenId by rememberSaveable { mutableStateOf(callerId) }
    var voiceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var originalVoiceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var loadedEvent by rememberSaveable { mutableStateOf(!editing) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var exact by remember { mutableStateOf(vm.exactAllowed()) }
    OnResume { exact = vm.exactAllowed() }

    LaunchedEffect(eventId) {
        if (!loadedEvent) {
            vm.event(eventId)?.let { e ->
                chosenId = e.callerId ?: chosenId
                val dt = Instant.ofEpochMilli(e.triggerAtMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
                date = dt.toLocalDate().toString()
                time = dt.toLocalTime().withSecond(0).withNano(0).toString()
                voiceUri = e.voiceUri
                voiceLabel = e.voiceLabel
                originalVoiceUri = e.voiceUri
            }
            loadedEvent = true
        }
    }

    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val label = vm.acceptVoiceFile(uri)
            if (label == null) {
                voiceError = "This file can't be played. Choose an MP3, M4A, WAV, OGG or FLAC file."
            } else {
                voiceError = null
                if (voiceUri != null && voiceUri != originalVoiceUri) vm.discardVoiceFile(voiceUri)
                voiceUri = uri.toString()
                voiceLabel = label
            }
        }
    }

    fun leave() {
        if (voiceUri != originalVoiceUri) vm.discardVoiceFile(voiceUri)
        nav.popBackStack()
    }
    BackHandler { leave() }

    val list = callers
    val caller: CallerEntity? = list?.firstOrNull { it.id == chosenId }
        ?: list?.firstOrNull { it.id == settings.lastCallerId }
        ?: list?.firstOrNull()

    fun spec(): ScheduleSpec? = when (mode) {
        0 -> ScheduleSpec.Immediate
        1 -> amount.toLongOrNull()?.let { ScheduleSpec.AfterSeconds(it) }
        2 -> amount.toLongOrNull()?.let { ScheduleSpec.AfterMinutes(it) }
        else -> runCatching { ScheduleSpec.AtLocal(LocalDateTime.of(LocalDate.parse(date), LocalTime.parse(time))) }.getOrNull()
    }

    val summary: String? = run {
        val s = spec() ?: return@run null
        if (s == ScheduleSpec.Immediate) return@run "Rings as soon as you tap the button."
        when (val r = ScheduleCalculator.resolve(s, System.currentTimeMillis(), ZoneId.systemDefault())) {
            is ScheduleResult.Valid -> "Rings ${formatWhen(r.triggerAtMillis)} (${TimeFormat.countdown(r.triggerAtMillis - System.currentTimeMillis())})"
            is ScheduleResult.Invalid -> null
        }
    }

    fun submit() {
        val c = caller
        val s = spec()
        if (c == null) { error = "Choose a caller first."; return }
        if (s == null) { error = "Enter a valid number."; return }
        val clip = voiceUri?.let { ScheduleCoordinator.VoiceClip(it, voiceLabel) }
        scope.launch {
            saving = true
            error = null
            if (s == ScheduleSpec.Immediate) {
                val id = vm.startNow(c.id, clip)
                if (id != null) {
                    ctx.startActivity(CallActivity.intent(ctx, id))
                    nav.popBackStack()
                } else error = "Couldn't start the call."
            } else {
                when (val o = vm.schedule(c, s, if (editing) eventId else null, clip)) {
                    is ScheduleCoordinator.Outcome.Rejected -> error = o.problem.message()
                    is ScheduleCoordinator.Outcome.Scheduled -> {
                        val note = (if (o.exact) "" else " Exact timing is off, so it may arrive a few minutes late.") +
                            (SystemAccess.quietHint(ctx)?.let { " $it" } ?: "")
                        Toast.makeText(ctx, "Call set for ${formatWhen(o.event.triggerAtMillis)}.$note", Toast.LENGTH_LONG).show()
                        nav.popBackStack()
                    }
                }
            }
            saving = false
        }
    }

    Scaffold(topBar = { AppTopBar(if (editing) "Reschedule call" else "Schedule a call", onBack = { leave() }) }) { pad ->
        if (list == null) {
            Box(Modifier.padding(pad)) { LoadingBox() }
        } else if (list.isEmpty()) {
            Box(Modifier.padding(pad)) {
                EmptyState(Icons.Filled.Alarm, "Add a caller first", "A call needs someone to come from.", "Add caller") {
                    nav.navigate(Routes.callerEdit(0))
                }
            }
        } else {
            Column(
                Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (caller != null) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            CallerAvatar(caller.photoFile, caller.name, 48.dp)
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text(caller.name, style = MaterialTheme.typography.titleMedium)
                                if (caller.phone.isNotBlank()) Text(caller.displayNumber(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { showPicker = true }) { Text("Change") }
                        }
                    }
                }

                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modeLabels.forEachIndexed { i, label ->
                        SegmentedButton(
                            selected = mode == i,
                            onClick = { mode = i; error = null },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = modeLabels.size),
                        ) { Text(label, maxLines = 1) }
                    }
                }

                when (mode) {
                    1, 2 -> {
                        val presets = if (mode == 1) secondPresets else minutePresets
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { p ->
                                FilterChip(
                                    selected = amount == p.toString(),
                                    onClick = { amount = p.toString() },
                                    label = { Text(if (mode == 1) "$p s" else "$p min") },
                                )
                            }
                        }
                        OutlinedTextField(
                            value = amount,
                            onValueChange = { amount = it.filter(Char::isDigit).take(6) },
                            label = { Text(if (mode == 1) "Seconds from now" else "Minutes from now") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    3 -> {
                        val dateText = runCatching {
                            LocalDate.parse(date).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                        }.getOrDefault(date)
                        val timeText = runCatching {
                            LocalTime.parse(time).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                        }.getOrDefault(time)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f)) { Text(dateText) }
                            OutlinedButton(onClick = { showTime = true }, modifier = Modifier.weight(1f)) { Text(timeText) }
                        }
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("In 1 hour" to 60L, "In 3 hours" to 180L).forEach { (label, minutes) ->
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        val t = LocalDateTime.now().plusMinutes(minutes).withSecond(0).withNano(0)
                                        date = t.toLocalDate().toString()
                                        time = t.toLocalTime().toString()
                                    },
                                    label = { Text(label) },
                                )
                            }
                            FilterChip(
                                selected = false,
                                onClick = {
                                    date = LocalDate.now().plusDays(1).toString()
                                    time = LocalTime.of(8, 0).toString()
                                },
                                label = { Text("Tomorrow 8:00") },
                            )
                        }
                    }
                }

                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Caller voice (optional)", style = MaterialTheme.typography.titleSmall)
                        Text(
                            voiceLabel ?: "No clip. After you answer, nothing plays unless you add one for this call.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { voicePicker.launch(arrayOf("audio/*")) }) {
                                Text(if (voiceUri == null) "Add audio clip" else "Change clip")
                            }
                            if (voiceUri != null) {
                                TextButton(onClick = {
                                    if (voiceUri != originalVoiceUri) vm.discardVoiceFile(voiceUri)
                                    voiceUri = null
                                    voiceLabel = null
                                    voiceError = null
                                }) { Text("Remove") }
                            }
                        }
                        voiceError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    }
                }
                if (summary != null) Text(summary, style = MaterialTheme.typography.bodyMedium)
                if (mode != 0 && !exact) {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Exact timing is off", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Android may deliver this call a few minutes after the time you pick. " +
                                    "Allow exact alarms for second-level accuracy.",
                            )
                            TextButton(onClick = { SystemAccess.openExactAlarmSettings(ctx) }) { Text("Allow exact timing") }
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

                Button(onClick = { submit() }, enabled = !saving && caller != null, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        when {
                            mode == 0 -> "Start call now"
                            editing -> "Save changes"
                            else -> "Schedule call"
                        }
                    )
                }
                if (caller != null) {
                    OutlinedButton(onClick = { nav.navigate(Routes.preview(caller.id)) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Preview this caller")
                    }
                }
            }
        }
    }

    if (showPicker && list != null) {
        CallerPickDialog(
            callers = list,
            onPick = { chosenId = it.id; showPicker = false },
            onAdd = { showPicker = false; nav.navigate(Routes.callerEdit(0)) },
            onDismiss = { showPicker = false },
        )
    }
    if (showDate) {
        val todayUtc = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = runCatching { LocalDate.parse(date) }.getOrDefault(LocalDate.now())
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString()
                    }
                    showDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
    if (showTime) {
        val initial = runCatching { LocalTime.parse(time) }.getOrDefault(LocalTime.now())
        val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(ctx))
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = LocalTime.of(state.hour, state.minute).toString()
                    showTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}

@Composable
fun ScheduledScreen(nav: NavController, vm: ScheduleViewModel = viewModel()) {
    val events by vm.scheduled.collectAsStateWithLifecycle()
    var cancelling by remember { mutableStateOf<CallEventEntity?>(null) }
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val list = events

    Scaffold(topBar = { AppTopBar("Scheduled calls") }) { pad ->
        Box(Modifier.padding(pad)) {
            when {
                list == null -> LoadingBox()
                list.isEmpty() -> EmptyState(
                    Icons.Filled.Schedule, "Nothing scheduled",
                    "Calls you schedule appear here, where you can change or cancel them.",
                    "Schedule a call",
                ) { nav.navigate(Routes.schedule()) }
                else -> LazyColumn {
                    items(list, key = { it.id }) { e ->
                        ListItem(
                            headlineContent = { Text(e.callerName.ifBlank { e.callerPhone }) },
                            supportingContent = {
                                Column {
                                    Text("${formatWhen(e.triggerAtMillis)} · ${TimeFormat.countdown(e.triggerAtMillis - now)}")
                                    if (e.voiceLabel != null) Text("Voice: ${e.voiceLabel}", style = MaterialTheme.typography.labelSmall)
                                    Text(
                                        if (e.exactAlarm) "Exact timing" else "Approximate timing",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            leadingContent = { CallerAvatar(e.callerPhotoFile, e.callerName, 44.dp) },
                            trailingContent = {
                                Row {
                                    IconButton(onClick = { nav.navigate(Routes.schedule(e.callerId ?: -1L, e.id, 3)) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Reschedule")
                                    }
                                    IconButton(onClick = { cancelling = e }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Cancel call")
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    cancelling?.let { e ->
        ConfirmDialog(
            title = "Cancel this call?",
            text = "The call from ${e.callerName.ifBlank { e.callerPhone }} set for ${formatWhen(e.triggerAtMillis)} will not ring.",
            confirmLabel = "Cancel call",
            onConfirm = { vm.cancel(e.id); cancelling = null },
            onDismiss = { cancelling = null },
        )
    }
}
