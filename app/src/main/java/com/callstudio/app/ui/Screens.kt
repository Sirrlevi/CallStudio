package com.callstudio.app.ui

import android.Manifest
import android.app.AlarmManager
import android.app.DatePickerDialog
import android.app.NotificationManager
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Settings
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.callstudio.app.data.CallHistory
import com.callstudio.app.data.CallerProfile
import com.callstudio.app.data.RecordingItem
import com.callstudio.app.data.ScheduledCall
import com.callstudio.app.settings.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

@Composable
fun CallersScreen(
    padding: PaddingValues,
    callers: List<CallerProfile>,
    onAdd: () -> Unit,
    onEdit: (CallerProfile) -> Unit,
    onDelete: (CallerProfile) -> Unit,
    onCall: (CallerProfile) -> Unit,
    onSchedule: (CallerProfile) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<CallerProfile?>(null) }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Saved callers", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Profiles you can reuse in any schedule", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = onAdd) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(5.dp)); Text("Add") }
        }
        Spacer(Modifier.height(12.dp))
        if (callers.isEmpty()) {
            EmptyState("No callers yet", "Create a custom profile or select a contact to begin.", Icons.Outlined.Call)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 16.dp)) {
                items(callers, key = { it.id }) { person ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CallerAvatar(person.photoUri, person.name, 54.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(person.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(person.phone.ifBlank { "No number" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(onClick = { onEdit(person) }) { Icon(Icons.Outlined.Edit, null); Text("Edit") }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { onCall(person) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(6.dp)); Text("Call now") }
                                OutlinedButton(onClick = { onSchedule(person) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Schedule, null); Spacer(Modifier.width(6.dp)); Text("Schedule") }
                                TextButton(onClick = { pendingDelete = person }) { Icon(Icons.Outlined.Delete, null) }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { person ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete caller profile?") },
            text = { Text("${person.name} will be removed from saved callers. Existing scheduled items may no longer be able to use this profile.") },
            confirmButton = { Button(onClick = { onDelete(person); pendingDelete = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
fun ScheduledScreen(
    padding: PaddingValues,
    schedules: List<ScheduledCall>,
    callers: List<CallerProfile>,
    onCancel: (ScheduledCall) -> Unit,
    onEdit: (ScheduledCall) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Upcoming calls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Upcoming items are saved locally on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        if (schedules.isEmpty()) EmptyState("Nothing scheduled", "Choose a caller and set a delay or a specific time.", Icons.Outlined.Schedule)
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(schedules, key = { it.id }) { item ->
                val person = callers.firstOrNull { it.id == item.callerId }
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(person?.name ?: "Saved caller unavailable", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.triggerAtMillis)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Status: scheduled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(5.dp)); Text("Edit") }
                            TextButton(onClick = { onCancel(item) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Delete, null); Spacer(Modifier.width(5.dp)); Text("Cancel") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(
    padding: PaddingValues,
    history: List<CallHistory>,
    recordings: List<RecordingItem>,
    onPlayRecording: (RecordingItem) -> Unit,
    onDeleteRecording: (RecordingItem) -> Unit
) {
    var showingRecordings by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("History", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 10.dp)) {
            FilterChip(selected = !showingRecordings, onClick = { showingRecordings = false }, label = { Text("Calls (${history.size})") })
            FilterChip(selected = showingRecordings, onClick = { showingRecordings = true }, label = { Text("Recordings (${recordings.size})") })
        }
        if (!showingRecordings) {
            if (history.isEmpty()) EmptyState("No call history", "Answered and declined sessions will appear here.", Icons.Outlined.Call)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(history, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.callerName, fontWeight = FontWeight.SemiBold)
                                Text("${item.phone} · ${item.outcome}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text("${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.startedAtMillis))} · ${formatDuration(item.durationSeconds)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        } else {
            if (recordings.isEmpty()) EmptyState("No recordings", "Microphone recordings created during an active session will appear here.", Icons.Outlined.Mic)
            else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(recordings, key = { it.id }) { item ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.label, fontWeight = FontWeight.SemiBold)
                                Text("${item.callerName} · ${formatDuration(item.durationMillis / 1000)}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                                Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.createdAtMillis)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onPlayRecording(item) }) { Icon(Icons.Outlined.PlayArrow, null) }
                            TextButton(onClick = { onDeleteRecording(item) }) { Icon(Icons.Outlined.Delete, null) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    padding: PaddingValues,
    settings: AppSettings,
    onTheme: (String) -> Unit,
    onChooseVoice: () -> Unit,
    onChooseRingtone: () -> Unit,
    onChooseCustomRingtone: () -> Unit,
    isPreviewingRingtone: Boolean,
    onPreviewRingtone: () -> Unit,
    onVibration: (Boolean) -> Unit,
    onRepeatVoice: (Boolean) -> Unit,
    onClearVoice: () -> Unit
) {
    val context = LocalContext.current
    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text("Settings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Choose a visual style and configure local audio and Android access.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Palette, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Call screen theme", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        val themes = listOf("Pixel / Android", "Truecaller style", "Vivo Call UI", "Samsung One UI", "iOS inspired", "Minimal dark", "Minimal light")
                        themes.forEach { theme ->
                            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTheme(theme) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = settings.theme == theme, onClick = { onTheme(theme) })
                                Column(Modifier.weight(1f)) {
                                    Text(theme, fontWeight = if (settings.theme == theme) FontWeight.SemiBold else FontWeight.Normal)
                                    Text(themeDescription(theme), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.MusicNote, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Audio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        Button(onClick = onChooseRingtone, modifier = Modifier.fillMaxWidth()) { Text("Choose system ringtone") }
                        OutlinedButton(onClick = onChooseCustomRingtone, modifier = Modifier.fillMaxWidth()) { Text("Import custom ringtone file") }
                        OutlinedButton(onClick = onPreviewRingtone, modifier = Modifier.fillMaxWidth()) { Text(if (isPreviewingRingtone) "Stop ringtone preview" else "Preview ringtone") }
                        Text(if (settings.ringtoneUri == null) "Using the device's default ringtone" else "A ringtone selection is saved", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Divider()
                        OutlinedButton(onClick = onChooseVoice, modifier = Modifier.fillMaxWidth()) { Text("Import caller voice audio") }
                        Text(if (settings.voiceUri == null) "No caller audio selected" else "Caller audio selected · plays locally after answering", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (settings.voiceUri != null) TextButton(onClick = onClearVoice) { Text("Clear caller audio") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Repeat caller audio", fontWeight = FontWeight.Medium); Text("Keeps playing until you pause it or end the session.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = settings.repeatVoice, onCheckedChange = onRepeatVoice)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text("Vibration while ringing", fontWeight = FontWeight.Medium); Text("Uses vibration settings allowed by Android.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Switch(checked = settings.vibration, onCheckedChange = onVibration)
                        }
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Settings, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("Android access", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        val notificationsOk = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                        val alarmManager = context.getSystemService(AlarmManager::class.java)
                        val exactOk = Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms()
                        val fullScreenOk = Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
                        StatusRow("Notifications", notificationsOk, if (notificationsOk) "Permission available" else "Needed to show scheduled calls") {
                            if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        StatusRow("Exact alarms", exactOk, if (exactOk) "Exact scheduling access available" else "Without this, Android may delay a call") {
                            if (Build.VERSION.SDK_INT >= 31) try { context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).setData(Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { }
                        }
                        StatusRow("Full-screen incoming call", fullScreenOk, if (fullScreenOk) "Full-screen intent access available" else "Allow full-screen notifications for lock-screen UI") {
                            if (Build.VERSION.SDK_INT >= 34) try { context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).setData(Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { }
                        }
                        Text("Android may still show a heads-up notification instead of opening the full screen. Battery restrictions and force-stop behaviour are controlled by the device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text("About CallStudio", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Imported audio plays on this device. Microphone recordings are saved locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusRow(title: String, ok: Boolean, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (ok) Icons.Outlined.CheckCircle else Icons.Outlined.Settings, null, tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onClick) { Text(if (ok) "Manage" else "Fix") }
    }
}

@Composable
fun CallerEditorDialog(existing: CallerProfile?, onDismiss: () -> Unit, onSave: (CallerProfile) -> Unit) {
    val context = LocalContext.current
    var name by rememberSaveable(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    var phone by rememberSaveable(existing?.id) { mutableStateOf(existing?.phone.orEmpty()) }
    var photoUri by rememberSaveable(existing?.id) { mutableStateOf(existing?.photoUri) }
    var expandedContact by remember { mutableStateOf(false) }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) { }
            photoUri = uri.toString()
        }
    }
    val contactsPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            try {
                var selectedContactId: String? = null
                val phoneProjection = arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                )
                context.contentResolver.query(uri, phoneProjection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val idIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                        if (nameIndex >= 0) name = cursor.getString(nameIndex).orEmpty()
                        if (numberIndex >= 0) phone = cursor.getString(numberIndex).orEmpty()
                        if (idIndex >= 0) selectedContactId = cursor.getString(idIndex)
                    }
                }

                var selectedPhoto: String? = null
                if (!selectedContactId.isNullOrBlank()) {
                    val photoProjection = arrayOf(
                        ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.PHOTO_THUMBNAIL_URI
                    )
                    context.contentResolver.query(
                        ContactsContract.Contacts.CONTENT_URI,
                        photoProjection,
                        "${ContactsContract.Contacts._ID} = ?",
                        arrayOf(selectedContactId),
                        null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val fullIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                            val thumbIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_THUMBNAIL_URI)
                            val fullPhoto = if (fullIndex >= 0) cursor.getString(fullIndex) else null
                            val thumbnail = if (thumbIndex >= 0) cursor.getString(thumbIndex) else null
                            selectedPhoto = fullPhoto?.takeIf { it.isNotBlank() }
                                ?: thumbnail?.takeIf { it.isNotBlank() }
                        }
                    }
                }
                // Replace an old photo with the selected contact's photo, if available.
                photoUri = selectedPhoto
            } catch (_: Exception) { }
        }
    }
    val contactPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) contactsPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create caller" else "Edit caller") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Caller name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone number (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { imagePicker.launch(arrayOf("image/*")) }, modifier = Modifier.weight(1f)) { Text(if (photoUri == null) "Choose photo" else "Change photo") }
                    OutlinedButton(onClick = {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                            contactsPicker.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        } else contactPermission.launch(Manifest.permission.READ_CONTACTS)
                    }, modifier = Modifier.weight(1f)) { Text("Select contact") }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CallerAvatar(photoUri, name.ifBlank { "Caller" }, 52.dp)
                    Column {
                        Text(
                            if (photoUri != null) "Contact photo linked" else "No contact photo",
                            color = if (photoUri != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            "You can still choose a different image. Your address book is not modified.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val cleanedName = name.trim()
                if (cleanedName.isNotEmpty()) onSave(CallerProfile(existing?.id ?: 0L, cleanedName, phone.trim(), photoUri))
            }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ScheduleEditorDialog(
    callers: List<CallerProfile>,
    existing: ScheduledCall?,
    initialCallerId: Long? = null,
    onDismiss: () -> Unit,
    onSave: (callerId: Long, triggerAt: Long, old: ScheduledCall?) -> Unit
) {
    val context = LocalContext.current
    val start = existing?.triggerAtMillis?.let { ((it - System.currentTimeMillis()) / 1_000L).coerceAtLeast(1L) } ?: 30L
    var secondsText by rememberSaveable(existing?.id) { mutableStateOf(start.toString()) }
    var callerId by rememberSaveable(existing?.id) { mutableStateOf(existing?.callerId ?: initialCallerId ?: callers.firstOrNull()?.id ?: 0L) }
    var expanded by remember { mutableStateOf(false) }
    var modeAtTime by rememberSaveable(existing?.id) { mutableStateOf(existing != null) }
    var delayInMinutes by rememberSaveable(existing?.id) { mutableStateOf(false) }
    var chosenAt by rememberSaveable(existing?.id) { mutableStateOf<Long?>(existing?.triggerAtMillis) }
    var saving by rememberSaveable(existing?.id) { mutableStateOf(false) }
    val selectedCaller = callers.firstOrNull { it.id == callerId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Schedule a call" else "Edit scheduled call") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedCaller?.name ?: "Choose caller") }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        callers.forEach { caller -> DropdownMenuItem(text = { Text(caller.name) }, onClick = { callerId = caller.id; expanded = false }) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !modeAtTime, onClick = { modeAtTime = false }, label = { Text("After delay") })
                    FilterChip(selected = modeAtTime, onClick = { modeAtTime = true }, label = { Text("Specific time") })
                }
                if (!modeAtTime) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = !delayInMinutes, onClick = { delayInMinutes = false }, label = { Text("Seconds") })
                        FilterChip(selected = delayInMinutes, onClick = { delayInMinutes = true }, label = { Text("Minutes") })
                    }
                    OutlinedTextField(
                        value = secondsText,
                        onValueChange = { secondsText = it.filter(Char::isDigit).take(7) },
                        label = { Text(if (delayInMinutes) "Delay in minutes" else "Delay in seconds") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (if (delayInMinutes) listOf(1, 2, 5, 10) else listOf(5, 10, 30, 60)).forEach { value ->
                            AssistChip(onClick = { secondsText = value.toString() }, label = { Text(if (delayInMinutes) "${value}m" else "${value}s") })
                        }
                    }
                } else {
                    val selectedTimeText = chosenAt?.let { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)) } ?: "No time selected"
                    Text(selectedTimeText, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = {
                        val initial = Calendar.getInstance().apply { chosenAt?.let { timeInMillis = it } }
                        DatePickerDialog(context, { _, year, month, day ->
                            val picked = Calendar.getInstance().apply {
                                set(Calendar.YEAR, year); set(Calendar.MONTH, month); set(Calendar.DAY_OF_MONTH, day)
                            }
                            TimePickerDialog(context, { _, hour, minute ->
                                picked.set(Calendar.HOUR_OF_DAY, hour); picked.set(Calendar.MINUTE, minute); picked.set(Calendar.SECOND, 0); picked.set(Calendar.MILLISECOND, 0)
                                chosenAt = picked.timeInMillis
                            }, initial.get(Calendar.HOUR_OF_DAY), initial.get(Calendar.MINUTE), true).show()
                        }, initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH)).show()
                    }, modifier = Modifier.fillMaxWidth()) { Text("Choose date and time") }
                }
                Text("Android may delay the trigger if exact-alarm access is unavailable or the device restricts background work.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (selectedCaller == null) return@Button
                val trigger = if (modeAtTime) chosenAt ?: return@Button else {
                    val entered = secondsText.toLongOrNull() ?: return@Button
                    if (entered < 1L) return@Button
                    val seconds = entered * if (delayInMinutes) 60L else 1L
                    try { com.callstudio.app.scheduling.SchedulerMath.afterSeconds(System.currentTimeMillis(), seconds) } catch (_: IllegalArgumentException) { return@Button }
                }
                if (trigger <= System.currentTimeMillis()) return@Button
                onSave(selectedCaller.id, trigger, existing)
            }, enabled = callers.isNotEmpty()) { Text("Save schedule") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun CallerAvatar(photoUri: String?, name: String, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = photoUri) {
        value = if (photoUri.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            try { context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } } catch (_: Exception) { null }
        }
    }
    if (bitmap != null) Image(bitmap = bitmap!!, contentDescription = "$name profile photo", modifier = Modifier.size(size).clip(CircleShape))
    else Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
        Text(name.trim().take(1).uppercase().ifBlank { "?" }, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun EmptyState(title: String, message: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp, horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, modifier = Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun themeDescription(name: String): String = when (name) {
    "Pixel / Android" -> "Rounded modern Android styling"
    "Samsung One UI" -> "Deep surfaces with soft blue accents"
    "iOS inspired" -> "Light clean surfaces and blue controls"
    "Vivo inspired" -> "Fresh teal and airy spacing"
    "Minimal dark" -> "Monochrome dark interface"
    else -> "Bright monochrome interface"
}

private fun formatDuration(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}
