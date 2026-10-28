package com.callstudio.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Contacts
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.callstudio.app.data.CallHistory
import com.callstudio.app.data.CallStudioDatabase
import com.callstudio.app.data.CallerProfile
import com.callstudio.app.data.RecordingItem
import com.callstudio.app.data.ScheduledCall
import com.callstudio.app.scheduling.CallExtras
import com.callstudio.app.scheduling.CallScheduler
import com.callstudio.app.scheduling.SchedulerMath
import com.callstudio.app.settings.AppSettings
import com.callstudio.app.settings.setRepeatVoicePreference
import com.callstudio.app.settings.setRingtonePreference
import com.callstudio.app.settings.setThemePreference
import com.callstudio.app.settings.setVibrationPreference
import com.callstudio.app.settings.setVoicePreference
import com.callstudio.app.settings.settingsFlow
import com.callstudio.app.ui.theme.schemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val db by lazy { CallStudioDatabase.get(this) }
    private var restoringActiveSession = false
    override fun onResume() {
        super.onResume()
        val session = ActiveSessionStore.read(this)
        if (!restoringActiveSession && session != null) {
            restoringActiveSession = true
            startActivity(Intent(this, IncomingCallActivity::class.java).apply {
                putExtra(CallExtras.EXTRA_SCHEDULED_ID, session.scheduledId)
                putExtra(CallExtras.EXTRA_CALLER_ID, session.callerId)
                putExtra(CallExtras.EXTRA_NAME, session.name)
                putExtra(CallExtras.EXTRA_PHONE, session.phone)
                putExtra(CallExtras.EXTRA_PHOTO_URI, session.photoUri)
                putExtra(CallExtras.EXTRA_VOICE_URI, session.voiceUri)
                putExtra(CallExtras.EXTRA_REPEAT_VOICE, session.repeatVoice)
                putExtra(CallExtras.EXTRA_ACTIVE, true)
            })
        } else if (session == null) {
            restoringActiveSession = false
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 901)
        }
        setContent { CallStudioRoot(db) }
    }
}

private enum class AppTab(val label: String) { HOME("Home"), CALLERS("Callers"), SCHEDULED("Scheduled"), HISTORY("History"), SETTINGS("Settings") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallStudioRoot(db: CallStudioDatabase) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by context.settingsFlow().collectAsState(initial = AppSettings())
    val callers by db.callerDao().observeAll().collectAsState(initial = emptyList())
    val schedules by db.scheduledCallDao().observePending().collectAsState(initial = emptyList())
    val history by db.callHistoryDao().observeAll().collectAsState(initial = emptyList())
    val recordings by db.recordingDao().observeAll().collectAsState(initial = emptyList())
    var selectedTab by rememberSaveable { mutableStateOf(AppTab.HOME.name) }
    var showCallerDialog by remember { mutableStateOf(false) }
    var editingCaller by remember { mutableStateOf<CallerProfile?>(null) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<ScheduledCall?>(null) }
    var preferredCallerId by remember { mutableStateOf<Long?>(null) }
    var isPreviewingRingtone by remember { mutableStateOf(false) }

    val voicePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            scope.launch { context.setVoicePreference(uri.toString()) }
        }
    }
    val ringtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val pickedKey = android.media.RingtoneManager.EXTRA_RINGTONE_PICKED_URI
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(pickedKey, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(pickedKey)
            }
            scope.launch { context.setRingtonePreference(uri?.toString()) }
        }
    }
    val customRingtonePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            scope.launch { context.setRingtonePreference(uri.toString()) }
        }
    }

    fun startImmediate(caller: CallerProfile) {
        context.startActivity(Intent(context, IncomingCallActivity::class.java).apply {
            putExtra(CallExtras.EXTRA_CALLER_ID, caller.id)
            putExtra(CallExtras.EXTRA_NAME, caller.name)
            putExtra(CallExtras.EXTRA_PHONE, caller.phone)
            putExtra(CallExtras.EXTRA_PHOTO_URI, caller.photoUri)
            putExtra(CallExtras.EXTRA_VOICE_URI, settings.voiceUri)
            putExtra(CallExtras.EXTRA_REPEAT_VOICE, settings.repeatVoice)
            putExtra(CallExtras.EXTRA_SCHEDULED_ID, 0L)
            putExtra(CallExtras.EXTRA_ACTIVE, false)
        })
    }

    fun quickSchedule(caller: CallerProfile, seconds: Long) {
        scope.launch {
            val trigger = SchedulerMath.afterSeconds(System.currentTimeMillis(), seconds)
            val raw = ScheduledCall(
                callerId = caller.id, triggerAtMillis = trigger,
                voiceUri = settings.voiceUri, repeatVoice = settings.repeatVoice
            )
            val id = withContext(Dispatchers.IO) { db.scheduledCallDao().insert(raw) }
            val item = raw.copy(id = id)
            val set = CallScheduler.schedule(context, item)
            if (!set) {
                withContext(Dispatchers.IO) { db.scheduledCallDao().updateState(id, "FAILED") }
                Toast.makeText(context, "Could not schedule this call. Check alarm access.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(context, "Call scheduled for ${seconds}s from now", Toast.LENGTH_SHORT).show()
            }
        }
    }

    MaterialTheme(colorScheme = schemeFor(settings.theme)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("CALLSTUDIO", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                            Text(selectedTab.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                NavigationBar {
                    AppTab.entries.forEach { item ->
                        val icon = when (item) {
                            AppTab.HOME -> Icons.Outlined.Home
                            AppTab.CALLERS -> Icons.Outlined.Contacts
                            AppTab.SCHEDULED -> Icons.Outlined.Schedule
                            AppTab.HISTORY -> Icons.Outlined.History
                            AppTab.SETTINGS -> Icons.Outlined.Settings
                        }
                        NavigationBarItem(
                            selected = selectedTab == item.name,
                            onClick = { selectedTab = item.name },
                            icon = { Icon(icon, contentDescription = item.label) },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        ) { padding ->
            when (AppTab.valueOf(selectedTab)) {
                AppTab.HOME -> HomeScreen(
                    padding, callers,
                    onCallNow = { if (callers.isEmpty()) showCallerDialog = true else startImmediate(callers.first()) },
                    onCreateCaller = { editingCaller = null; showCallerDialog = true },
                    onSchedule = { if (callers.isEmpty()) showCallerDialog = true else { editingSchedule = null; showScheduleDialog = true } },
                    onQuick = { seconds -> if (callers.isEmpty()) showCallerDialog = true else quickSchedule(callers.first(), seconds) },
                    settings = settings
                )
                AppTab.CALLERS -> CallersScreen(
                    padding, callers,
                    onAdd = { editingCaller = null; showCallerDialog = true },
                    onEdit = { editingCaller = it; showCallerDialog = true },
                    onDelete = { person -> scope.launch { withContext(Dispatchers.IO) { db.callerDao().delete(person) } } },
                    onCall = { startImmediate(it) },
                    onSchedule = { person -> preferredCallerId = person.id; editingSchedule = null; showScheduleDialog = true }
                )
                AppTab.SCHEDULED -> ScheduledScreen(
                    padding, schedules, callers,
                    onCancel = { item -> scope.launch { CallScheduler.cancel(context, item.id); withContext(Dispatchers.IO) { db.scheduledCallDao().updateState(item.id, "CANCELLED") } } },
                    onEdit = { item -> editingSchedule = item; showScheduleDialog = true }
                )
                AppTab.HISTORY -> HistoryScreen(
                    padding, history, recordings,
                    onPlayRecording = { item -> com.callstudio.app.audio.AudioClipPlayer.play(context, Uri.fromFile(File(item.filePath)), false) },
                    onDeleteRecording = { item -> scope.launch { com.callstudio.app.audio.AudioClipPlayer.stop(); withContext(Dispatchers.IO) { File(item.filePath).delete(); db.recordingDao().delete(item) } } }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    padding, settings,
                    onTheme = { value -> scope.launch { context.setThemePreference(value) } },
                    onChooseVoice = { voicePicker.launch(arrayOf("audio/*")) },
                    onChooseRingtone = {
                        val intent = Intent(android.media.RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TYPE, android.media.RingtoneManager.TYPE_RINGTONE)
                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_TITLE, "Choose incoming ringtone")
                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            settings.ringtoneUri?.let { putExtra(android.media.RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(it)) }
                        }
                        ringtonePicker.launch(intent)
                    },
                    onChooseCustomRingtone = { customRingtonePicker.launch(arrayOf("audio/*")) },
                    isPreviewingRingtone = isPreviewingRingtone,
                    onPreviewRingtone = {
                        isPreviewingRingtone = com.callstudio.app.audio.RingtonePreviewPlayer.toggle(context, settings.ringtoneUri)
                    },
                    onVibration = { scope.launch { context.setVibrationPreference(it) } },
                    onRepeatVoice = { scope.launch { context.setRepeatVoicePreference(it) } },
                    onClearVoice = { scope.launch { context.setVoicePreference(null) } }
                )
            }
        }
    }

    if (showCallerDialog) {
        CallerEditorDialog(
            existing = editingCaller,
            onDismiss = { showCallerDialog = false; editingCaller = null },
            onSave = { item ->
                scope.launch {
                    withContext(Dispatchers.IO) {
                        if (item.id == 0L) db.callerDao().insert(item) else db.callerDao().update(item)
                    }
                    showCallerDialog = false
                    editingCaller = null
                }
            }
        )
    }

    if (showScheduleDialog) {
        ScheduleEditorDialog(
            callers = callers,
            existing = editingSchedule,
            initialCallerId = preferredCallerId,
            onDismiss = { showScheduleDialog = false; editingSchedule = null; preferredCallerId = null },
            onSave = { callerId, triggerAt, old ->
                scope.launch {
                    val raw = ScheduledCall(
                        id = old?.id ?: 0L,
                        callerId = callerId,
                        triggerAtMillis = triggerAt,
                        voiceUri = settings.voiceUri,
                        repeatVoice = settings.repeatVoice,
                        state = "SCHEDULED",
                        createdAtMillis = old?.createdAtMillis ?: System.currentTimeMillis()
                    )
                    val id = withContext(Dispatchers.IO) {
                        if (old == null) db.scheduledCallDao().insert(raw) else { db.scheduledCallDao().update(raw); old.id }
                    }
                    val item = raw.copy(id = id)
                    CallScheduler.cancel(context, id)
                    val ok = CallScheduler.schedule(context, item)
                    if (!ok) {
                        withContext(Dispatchers.IO) { db.scheduledCallDao().updateState(id, "FAILED") }
                        Toast.makeText(context, "Scheduling failed. Check exact-alarm access.", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Call scheduled", Toast.LENGTH_SHORT).show()
                    }
                    showScheduleDialog = false
                    editingSchedule = null
                    preferredCallerId = null
                    selectedTab = AppTab.SCHEDULED.name
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    padding: PaddingValues,
    callers: List<CallerProfile>,
    onCallNow: () -> Unit,
    onCreateCaller: () -> Unit,
    onSchedule: () -> Unit,
    onQuick: (Long) -> Unit,
    settings: AppSettings
) {
    Column(
        Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("A call on your time.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Create a local incoming-call experience with your caller, timing and sound.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.Call, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Text("CALL EXPERIENCE STUDIO", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
                Button(onClick = onCallNow, modifier = Modifier.fillMaxWidth().height(54.dp)) {
                    Icon(Icons.Outlined.Call, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Start incoming call", fontWeight = FontWeight.SemiBold)
                }
                OutlinedButton(onClick = onSchedule, modifier = Modifier.fillMaxWidth().height(50.dp)) {
                    Icon(Icons.Outlined.Schedule, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    Text("Schedule a call")
                }
            }
        }
        Text("Quick schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            listOf(5L, 30L, 60L).forEach { seconds ->
                Card(modifier = Modifier.weight(1f).clickable { onQuick(seconds) }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(vertical = 16.dp, horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (seconds < 60) "${seconds}s" else "1 min", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text("after", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Caller profiles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (callers.isEmpty()) "Create a profile to get started" else "${callers.size} saved · ${settings.theme}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(onClick = onCreateCaller) { Text(if (callers.isEmpty()) "Create" else "Add") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Scheduling depends on Android alarm and notification access. Review Setup in Settings for reliable lock-screen behaviour.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
