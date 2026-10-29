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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Card
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
    val isTruecaller = themeName == "Truecaller style"
    val isVivo = themeName == "Vivo Call UI" || themeName == "Vivo inspired"
    val isSamsung = themeName == "Samsung One UI"
    val isIos = themeName == "iOS inspired"
    val isMinimalDark = themeName == "Minimal dark"
    val isMinimalLight = themeName == "Minimal light"
    val isMinimal = isMinimalDark || isMinimalLight
    val foreground = if (isMinimalLight) Color(0xFF202124) else Color.White
    val muted = if (isMinimalLight) Color(0xFF5F6368) else foreground.copy(alpha = 0.76f)
    val background = when {
        isTruecaller -> Brush.verticalGradient(listOf(Color(0xFF075FEA), Color(0xFF1168F5), Color(0xFF347FF4), Color(0xFF1558D8)))
        isVivo -> Brush.verticalGradient(listOf(Color(0xFF071821), Color(0xFF0B3D49), Color(0xFF16213B), Color(0xFF111729)))
        isSamsung -> Brush.verticalGradient(listOf(Color(0xFF080B11), Color(0xFF141A24), Color(0xFF0E121A)))
        isIos -> Brush.verticalGradient(listOf(Color(0xFF171719), Color(0xFF101012), Color(0xFF080809)))
        isMinimalLight -> Brush.verticalGradient(listOf(Color(0xFFF8F9FA), Color(0xFFECEFF3)))
        isMinimalDark -> Brush.verticalGradient(listOf(Color(0xFF080808), Color(0xFF171717)))
        else -> Brush.verticalGradient(listOf(Color(0xFF101A24), Color(0xFF1A2A39), Color(0xFF101820)))
    }

    Box(Modifier.fillMaxSize().background(background)) {
        when {
            isTruecaller -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CALLSTUDIO  •  CALLER ID", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(7.dp))
                    Text("Incoming call · ${phone.ifBlank { "Mobile" }}", color = Color.White.copy(alpha = 0.83f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.weight(0.65f))
                    Box(
                        Modifier.size(166.dp).background(Color.White.copy(alpha = 0.15f), CircleShape).padding(12.dp)
                            .background(Color.White.copy(alpha = 0.10f), CircleShape).padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) { CallerAvatar(photoUri, name, 136.dp) }
                    Spacer(Modifier.height(24.dp))
                    Text(name, color = Color.White, fontSize = 31.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(phone.ifBlank { "Mobile" }, color = Color.White.copy(alpha = 0.86f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(18.dp))
                    Box(Modifier.background(Color.White.copy(alpha = 0.17f), RoundedCornerShape(50.dp)).padding(horizontal = 18.dp, vertical = 9.dp)) {
                        Text("Saved caller profile", color = Color.White, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        IncomingCallControl("Decline", false, "truecaller", onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, "truecaller", onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Simulated call · no real phone call is placed", color = Color.White.copy(alpha = 0.68f), style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center)
                }
            }

            isSamsung -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("INCOMING CALL", color = Color(0xFFA9BDEB), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.2.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.weight(0.72f))
                    CallerAvatar(photoUri, name, 142.dp)
                    Spacer(Modifier.height(28.dp))
                    Text(name, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(9.dp))
                    Text(phone.ifBlank { "Mobile" }, color = Color(0xFFCBD1DD), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(7.dp))
                    Text("Mobile · Incoming", color = Color(0xFF8994A8), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1.15f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IncomingCallControl("Decline", false, "samsung", onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, "samsung", onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(28.dp))
                }
            }

            isVivo -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("INCOMING CALL", color = Color(0xFF6BE7DE), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.7.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(0.58f))
                    Box(
                        Modifier.size(178.dp).background(Color(0xFF00D4C4).copy(alpha = 0.12f), CircleShape).padding(10.dp)
                            .background(Color(0xFF00D4C4).copy(alpha = 0.13f), CircleShape).padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) { CallerAvatar(photoUri, name, 138.dp) }
                    Spacer(Modifier.height(26.dp))
                    Text(name, color = Color.White, fontSize = 29.sp, lineHeight = 35.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(phone.ifBlank { "Mobile" }, color = Color(0xFFB5D4D8), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    Text("CallStudio incoming call", color = Color(0xFF719BA3), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(0.9f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        IncomingCallControl("Decline", false, "vivo", onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, "vivo", onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }

            isIos -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(8.dp))
                    Text("Call via Mobile", color = Color.White.copy(alpha = 0.83f), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = Color.White, fontSize = 31.sp, lineHeight = 37.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
                    Text(phone.ifBlank { "Mobile" }, color = Color.White.copy(alpha = 0.74f), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(0.8f))
                    Box(Modifier.size(178.dp).background(Color.White.copy(alpha = 0.05f), CircleShape).padding(9.dp), contentAlignment = Alignment.Center) {
                        CallerAvatar(photoUri, name, 160.dp)
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        IncomingCallControl("Decline", false, "ios", onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, "ios", onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }

            isMinimal -> {
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 28.dp)) {
                    Text("INCOMING CALL", color = muted, style = MaterialTheme.typography.labelLarge, letterSpacing = 1.8.sp)
                    Spacer(Modifier.height(44.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                        CallerAvatar(photoUri, name, 84.dp)
                        Column(Modifier.weight(1f)) {
                            Text(name, color = foreground, fontSize = 25.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(5.dp))
                            Text(phone.ifBlank { "Mobile" }, color = muted, style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(4.dp))
                            Text("CallStudio", color = muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IncomingCallControl("Decline", false, themeName, onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, themeName, onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            else -> {
                // Pixel / Android: clear identity first, with wide, tactile call actions.
                Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("INCOMING CALL", color = Color(0xFFB8D3C4), style = MaterialTheme.typography.labelLarge, letterSpacing = 2.sp)
                    Spacer(Modifier.weight(0.62f))
                    CallerAvatar(photoUri, name, 142.dp)
                    Spacer(Modifier.height(24.dp))
                    Text(name, color = Color.White, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Text(phone.ifBlank { "Mobile" }, color = Color.White.copy(alpha = 0.76f), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Calling…", color = Color(0xFFB8D3C4), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IncomingCallControl("Decline", false, "pixel", onDecline, Modifier.weight(1f))
                        IncomingCallControl("Answer", true, "pixel", onAnswer, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(18.dp))
                }
            }
        }
    }
}

@Composable
private fun IncomingCallControl(
    label: String,
    answer: Boolean,
    style: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isTruecaller = style == "truecaller"
    val isVivo = style == "vivo"
    val isSamsung = style == "samsung"
    val isIos = style == "ios"
    val isMinimal = style.startsWith("Minimal")
    val wide = isVivo || style == "pixel" || isMinimal
    val actionColor = when {
        isTruecaller -> Color.White
        isVivo && answer -> Color(0xFF00BFAF)
        isVivo -> Color(0xFFEF5963)
        answer && isSamsung -> Color(0xFF35B86B)
        !answer && isSamsung -> Color(0xFFEA5C68)
        answer && isIos -> Color(0xFF30C65A)
        !answer && isIos -> Color(0xFFFF453A)
        answer -> Color(0xFF1FA66A)
        else -> Color(0xFFEA4D57)
    }
    val iconColor = if (isTruecaller) {
        if (answer) Color(0xFF138B61) else Color(0xFFDF4050)
    } else Color.White
    val labelColor = if (isMinimal) {
        if (style == "Minimal light") Color(0xFF202124) else Color(0xFFE7E7E7)
    } else Color.White.copy(alpha = 0.88f)

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (isMinimal) {
            OutlinedButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (answer) Color(0xFF28A66A) else Color(0xFFE24B55)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (answer) Color(0xFF28A66A) else Color(0xFFE24B55)),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(if (answer) Icons.Outlined.Call else Icons.Outlined.CallEnd, contentDescription = label, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(7.dp))
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        } else {
            Button(
                onClick = onClick,
                modifier = if (wide) Modifier.fillMaxWidth().height(58.dp) else Modifier.size(if (isSamsung) 76.dp else 82.dp),
                shape = if (wide) RoundedCornerShape(if (isVivo) 24.dp else 18.dp) else CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = actionColor, contentColor = iconColor),
                contentPadding = if (wide) PaddingValues(horizontal = 13.dp, vertical = 8.dp) else PaddingValues(0.dp)
            ) {
                Icon(if (answer) Icons.Outlined.Call else Icons.Outlined.CallEnd, contentDescription = label, modifier = Modifier.size(24.dp))
                if (wide) {
                    Spacer(Modifier.width(8.dp))
                    Text(label, fontWeight = FontWeight.SemiBold)
                }
            }
            if (!wide) Text(label, color = labelColor, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
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
