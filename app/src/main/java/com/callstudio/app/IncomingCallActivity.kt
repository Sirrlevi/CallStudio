package com.callstudio.app

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CallEnd
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.callstudio.app.audio.AudioClipPlayer
import com.callstudio.app.audio.MicRecorder
import com.callstudio.app.data.CallHistory
import com.callstudio.app.data.CallStudioDatabase
import com.callstudio.app.data.RecordingItem
import com.callstudio.app.scheduling.CallExtras
import com.callstudio.app.scheduling.CallNotifications
import com.callstudio.app.settings.AppSettings
import com.callstudio.app.settings.settingsFlow
import com.callstudio.app.ui.CallerAvatar
import com.callstudio.app.ui.theme.schemeFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent { IncomingCallRoot(this) }
    }
}

@Composable
private fun IncomingCallRoot(activity: IncomingCallActivity) {
    val context = activity
    val db = remember { CallStudioDatabase.get(context) }
    val settings by context.settingsFlow().collectAsState(initial = AppSettings())
    val intent = activity.intent
    val scheduledId = intent.getLongExtra(CallExtras.EXTRA_SCHEDULED_ID, 0L)
    val callerId = intent.getLongExtra(CallExtras.EXTRA_CALLER_ID, 0L)
    val name = intent.getStringExtra(CallExtras.EXTRA_NAME) ?: "Unknown caller"
    val phone = intent.getStringExtra(CallExtras.EXTRA_PHONE).orEmpty()
    val photoUri = intent.getStringExtra(CallExtras.EXTRA_PHOTO_URI)
    val voiceUri = intent.getStringExtra(CallExtras.EXTRA_VOICE_URI) ?: settings.voiceUri
    val repeatVoice = intent.getBooleanExtra(CallExtras.EXTRA_REPEAT_VOICE, settings.repeatVoice)
    var active by rememberSaveable { mutableStateOf(intent.getBooleanExtra(CallExtras.EXTRA_ACTIVE, false)) }
    var sessionStartedAt by rememberSaveable { mutableStateOf(0L) }
    val startPreferenceKey = "session_started_$scheduledId"
    val scope = rememberCoroutineScope()

    LaunchedEffect(active, scheduledId) {
        if (!active && scheduledId == 0L) {
            val ringIntent = Intent(context, RingingService::class.java).apply {
                putExtra(CallExtras.EXTRA_SCHEDULED_ID, 0L)
                putExtra(CallExtras.EXTRA_CALLER_ID, callerId)
                putExtra(CallExtras.EXTRA_NAME, name)
                putExtra(CallExtras.EXTRA_PHONE, phone)
                putExtra(CallExtras.EXTRA_PHOTO_URI, photoUri)
                putExtra(CallExtras.EXTRA_VOICE_URI, voiceUri)
                putExtra(CallExtras.EXTRA_REPEAT_VOICE, repeatVoice)
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) androidx.core.content.ContextCompat.startForegroundService(context, ringIntent)
                else context.startService(ringIntent)
            } catch (_: Exception) { }
        }
        if (active) {
            RingingService.stop(context, scheduledId.takeIf { it > 0 })
            if (scheduledId > 0) CallNotifications.cancel(context, scheduledId)
            if (sessionStartedAt == 0L) {
                sessionStartedAt = context.getSharedPreferences("callstudio_sessions", Context.MODE_PRIVATE).getLong(startPreferenceKey, 0L).takeIf { it > 0L } ?: System.currentTimeMillis()
                context.getSharedPreferences("callstudio_sessions", Context.MODE_PRIVATE).edit().putLong(startPreferenceKey, sessionStartedAt).apply()
                ActiveSessionStore.start(context, scheduledId, callerId, name, phone, photoUri, voiceUri, repeatVoice, sessionStartedAt)
            }
            if (!voiceUri.isNullOrBlank()) {
                AudioClipPlayer.play(context, Uri.parse(voiceUri), repeatVoice)
            }
        }
    }

    BackHandler(enabled = true) { /* This session ends only through its explicit call controls. */ }

    MaterialTheme(colorScheme = schemeFor(settings.theme)) {
        AnimatedContent(targetState = active, label = "call-state") { isActive ->
            if (isActive) {
                ActiveCallScreen(
                    context = context, db = db, themeName = settings.theme, name = name, phone = phone, photoUri = photoUri,
                    scheduledId = scheduledId, sessionStartedAt = sessionStartedAt,
                    hasVoiceClip = !voiceUri.isNullOrBlank(),
                    onReplay = { if (!voiceUri.isNullOrBlank()) AudioClipPlayer.play(context, Uri.parse(voiceUri), repeatVoice) },
                    onPauseAudio = { AudioClipPlayer.togglePause() },
                    onEnd = {
                        AudioClipPlayer.stop()
                        RingingService.stop(context, scheduledId.takeIf { it > 0 })
                        CallNotifications.cancel(context, scheduledId)
                        val duration = ((System.currentTimeMillis() - sessionStartedAt).coerceAtLeast(0L) / 1000L)
                        AppIo.scope.launch {
                            db.callHistoryDao().insert(CallHistory(callerName = name, phone = phone, outcome = "Answered", startedAtMillis = sessionStartedAt, durationSeconds = duration, scheduledCallId = scheduledId.takeIf { it > 0 }))
                            if (scheduledId > 0) db.scheduledCallDao().updateState(scheduledId, "ENDED")
                        }
                        context.getSharedPreferences("callstudio_sessions", Context.MODE_PRIVATE).edit().remove(startPreferenceKey).apply()
                        ActiveSessionStore.clear(context)
                        activity.finish()
                    }
                )
            } else {
                IncomingScreen(
                    themeName = settings.theme,
                    name = name,
                    phone = phone,
                    photoUri = photoUri,
                    onAnswer = {
                        sessionStartedAt = System.currentTimeMillis()
                        context.getSharedPreferences("callstudio_sessions", Context.MODE_PRIVATE).edit().putLong(startPreferenceKey, sessionStartedAt).apply()
                        ActiveSessionStore.start(context, scheduledId, callerId, name, phone, photoUri, voiceUri, repeatVoice, sessionStartedAt)
                        active = true
                    },
                    onDecline = {
                        AudioClipPlayer.stop()
                        RingingService.stop(context, scheduledId.takeIf { it > 0 })
                        CallNotifications.cancel(context, scheduledId)
                        AppIo.scope.launch {
                            db.callHistoryDao().insert(CallHistory(callerName = name, phone = phone, outcome = "Declined", startedAtMillis = System.currentTimeMillis(), durationSeconds = 0, scheduledCallId = scheduledId.takeIf { it > 0 }))
                            if (scheduledId > 0) db.scheduledCallDao().updateState(scheduledId, "DECLINED")
                        }
                        activity.finish()
                    }
                )
            }
        }
    }
}

@Composable
private fun IncomingScreen(
    themeName: String,
    name: String,
    phone: String,
    photoUri: String?,
    onAnswer: () -> Unit,
    onDecline: () -> Unit
) {
    val bg = MaterialTheme.colorScheme.background
    val onBg = MaterialTheme.colorScheme.onBackground
    val minimal = themeName.startsWith("Minimal")
    val ios = themeName == "iOS inspired"
    val samsung = themeName == "Samsung One UI"
    val avatarSize = when { minimal -> 116.dp; ios -> 168.dp; samsung -> 132.dp; else -> 150.dp }
    val shell = if (themeName == "Vivo inspired") {
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), bg, bg)))
    } else Modifier.fillMaxSize().background(bg)
    Box(shell.padding(horizontal = if (samsung) 20.dp else 28.dp, vertical = if (minimal) 26.dp else 38.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(if (minimal) 8.dp else 24.dp))
            Text(
                when { ios -> "Call via Mobile"; samsung -> "Incoming call"; themeName == "Vivo inspired" -> "INCOMING CALL"; else -> "INCOMING CALL" },
                style = if (ios) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold,
                letterSpacing = if (minimal) 0.sp else 2.sp
            )
            Spacer(Modifier.height(if (minimal) 8.dp else 14.dp))
            Text(name, style = if (ios) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, color = onBg, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(if (phone.isBlank()) "Mobile" else if (ios) phone else "Mobile $phone", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(if (samsung) 0.8f else 1f))
            if (samsung) {
                Card(colors = androidx.compose.material3.CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(28.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CallerAvatar(photoUri, name, avatarSize)
                        Text("Mobile $phone", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(onClick = onDecline, modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85C58), contentColor = Color.White)) { Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(5.dp)); Text("Decline") }
                            Button(onClick = onAnswer, modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF57BC7A), contentColor = Color.White)) { Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(5.dp)); Text("Answer") }
                        }
                    }
                }
            } else {
                CallerAvatar(photoUri, name, avatarSize)
                Spacer(Modifier.height(if (minimal) 10.dp else 16.dp))
                if (!ios) Text(name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold)
                Text("calling…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (minimal || themeName == "Vivo inspired") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(if (minimal) 12.dp else 28.dp)) { Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(6.dp)); Text("Decline") }
                        Button(onClick = onAnswer, modifier = Modifier.weight(1f).height(54.dp), shape = RoundedCornerShape(if (minimal) 12.dp else 28.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF57BC7A), contentColor = Color.White)) { Icon(Icons.Outlined.Call, null); Spacer(Modifier.width(6.dp)); Text("Answer") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onDecline, modifier = Modifier.size(if (ios) 82.dp else 76.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85C58), contentColor = Color.White), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.CallEnd, contentDescription = "Decline call", modifier = Modifier.size(29.dp)) }
                            Spacer(Modifier.height(10.dp)); Text("Decline", color = Color(0xFFE85C58), fontWeight = FontWeight.SemiBold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Button(onClick = onAnswer, modifier = Modifier.size(if (ios) 82.dp else 76.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF57BC7A), contentColor = Color.White), contentPadding = PaddingValues(0.dp)) { Icon(Icons.Outlined.Call, contentDescription = "Answer call", modifier = Modifier.size(29.dp)) }
                            Spacer(Modifier.height(10.dp)); Text("Answer", color = Color(0xFF57BC7A), fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(if (minimal) 8.dp else 24.dp))
        }
    }
}

@Composable
private fun ActiveCallScreen(
    context: Context,
    db: CallStudioDatabase,
    themeName: String,
    name: String,
    phone: String,
    photoUri: String?,
    scheduledId: Long,
    sessionStartedAt: Long,
    hasVoiceClip: Boolean,
    onReplay: () -> Unit,
    onPauseAudio: () -> Unit,
    onEnd: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val recorder = remember { MicRecorder(context.applicationContext) }
    var recording by rememberSaveable { mutableStateOf(false) }
    var micMuted by rememberSaveable { mutableStateOf(false) }
    var speakerOn by rememberSaveable { mutableStateOf(true) }
    var audioPaused by rememberSaveable { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    var recordingStartedFromUi by remember { mutableStateOf(false) }
    val audioManager = remember { context.getSystemService(AudioManager::class.java) }
    val oldAudioMode = remember { audioManager.mode }
    val oldSpeakerState = remember { audioManager.isSpeakerphoneOn }

    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            try {
                recorder.start()
                if (micMuted) recorder.pause()
                recording = true
                recordingStartedFromUi = true
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "Could not start recording: ${e.message ?: "audio source unavailable"}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    LaunchedEffect(sessionStartedAt) {
        while (true) {
            elapsed = ((System.currentTimeMillis() - sessionStartedAt).coerceAtLeast(0L) / 1000L)
            delay(1000)
        }
    }

    LaunchedEffect(speakerOn) {
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            @Suppress("DEPRECATION")
            run { audioManager.isSpeakerphoneOn = speakerOn }
        } catch (_: Exception) { }
    }

    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            try { audioManager.isSpeakerphoneOn = oldSpeakerState; audioManager.mode = oldAudioMode } catch (_: Exception) { }
            if (recorder.isRecording && recordingStartedFromUi) {
                try { recorder.stop() } catch (_: Exception) { recorder.abandon() }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 24.dp, vertical = 35.dp)) {
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("CALL IN PROGRESS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(Modifier.height(22.dp))
            CallerAvatar(photoUri, name, when (themeName) { "iOS inspired" -> 156.dp; "Minimal dark", "Minimal light" -> 104.dp; else -> 132.dp })
            Spacer(Modifier.height(18.dp))
            Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Text(phone.ifBlank { "Mobile" }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(15.dp))
            Text(formatClock(elapsed), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(if (recording) if (micMuted) "Recording paused while muted" else "Microphone recording active" else "Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                ActiveControl(
                    icon = if (micMuted) Icons.Outlined.MicOff else Icons.Outlined.Mic,
                    label = if (micMuted) "Unmute mic" else "Mute mic",
                    selected = micMuted,
                    themeName = themeName,
                    enabled = recording,
                    onClick = {
                        micMuted = !micMuted
                        if (micMuted) recorder.pause() else recorder.resume()
                    }
                )
                ActiveControl(
                    icon = Icons.Outlined.Speaker,
                    label = if (speakerOn) "Speaker on" else "Speaker off",
                    selected = speakerOn,
                    themeName = themeName,
                    onClick = { speakerOn = !speakerOn }
                )
                ActiveControl(
                    icon = if (recording) Icons.Outlined.Stop else Icons.Outlined.RecordVoiceOver,
                    label = if (recording) "Stop rec" else "Record",
                    selected = recording,
                    themeName = themeName,
                    onClick = {
                        if (!recording) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                try { recorder.start(); if (micMuted) recorder.pause(); recording = true; recordingStartedFromUi = true } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "Could not start recording: ${e.message ?: "unavailable"}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            } else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        } else {
                            try {
                                val result = recorder.stop()
                                recording = false
                                recordingStartedFromUi = false
                                scope.launch(Dispatchers.IO) {
                                    db.recordingDao().insert(RecordingItem(
                                        filePath = result.path,
                                        label = "Microphone recording",
                                        callerName = name,
                                        createdAtMillis = System.currentTimeMillis(),
                                        durationMillis = result.durationMillis
                                    ))
                                }
                                android.widget.Toast.makeText(context, "Recording saved on this device", android.widget.Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                recording = false
                                android.widget.Toast.makeText(context, "Recording could not be saved", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(25.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { onPauseAudio(); audioPaused = !audioPaused }, enabled = hasVoiceClip, modifier = Modifier.weight(1f)) {
                    Icon(if (audioPaused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, null); Spacer(Modifier.width(6.dp)); Text(if (audioPaused) "Resume audio" else "Pause audio")
                }
                OutlinedButton(onClick = onReplay, enabled = hasVoiceClip, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Replay, null); Spacer(Modifier.width(6.dp)); Text("Replay clip") }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                onClick = {
                    if (recorder.isRecording) {
                        try {
                            val result = recorder.stop()
                            recording = false
                            recordingStartedFromUi = false
                            AppIo.scope.launch {
                                db.recordingDao().insert(RecordingItem(filePath = result.path, label = "Microphone recording", callerName = name, createdAtMillis = System.currentTimeMillis(), durationMillis = result.durationMillis))
                            }
                        } catch (_: Exception) { recorder.abandon() }
                    }
                    onEnd()
                },
                modifier = Modifier.fillMaxWidth().height(58.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE85C58), contentColor = Color.White)
            ) { Icon(Icons.Outlined.CallEnd, null); Spacer(Modifier.width(10.dp)); Text("End call", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun ActiveControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    themeName: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val controlShape = when (themeName) {
        "Samsung One UI" -> RoundedCornerShape(18.dp)
        "Minimal dark", "Minimal light" -> RoundedCornerShape(12.dp)
        "Vivo inspired" -> RoundedCornerShape(22.dp)
        else -> CircleShape
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.width(88.dp)) {
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            shape = controlShape,
            modifier = Modifier.size(if (themeName.startsWith("Minimal")) 52.dp else 60.dp)
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp)) }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatClock(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, secs) else "%02d:%02d".format(minutes, secs)
}
