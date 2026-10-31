@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.callstudio.app.audio.AudioFiles
import com.callstudio.app.audio.VoicePlayer
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.ui.components.AppTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Starts a normal carrier-billed cellular call through Android's phone app. The selected clip is
 * played locally only; Android does not expose a public API for injecting it into a SIM-call uplink.
 */
@Composable
fun OutgoingCallScreen(nav: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var number by rememberSaveable { mutableStateOf("") }
    var voiceUri by rememberSaveable { mutableStateOf<String?>(null) }
    var voiceLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var dialerOpened by rememberSaveable { mutableStateOf(false) }
    var simCallStarted by rememberSaveable { mutableStateOf(false) }

    val player = remember(context) { VoicePlayer(context.applicationContext, inCall = false) }
    val playerState by player.state.collectAsStateWithLifecycle()
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    fun openDialer(useDirectCall: Boolean) {
        val dialable = PhoneNumbers.dialable(number)
        if (dialable.count { it.isDigit() } < 5) {
            Toast.makeText(context, "Enter a valid phone number.", Toast.LENGTH_SHORT).show()
            return
        }
        val action = if (useDirectCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        val intent = Intent(action, Uri.fromParts("tel", dialable, null))
        try {
            context.startActivity(intent)
            dialerOpened = true
            simCallStarted = useDirectCall
        } catch (_: Exception) {
            Toast.makeText(context, "No phone dialer is available on this device.", Toast.LENGTH_LONG).show()
        }
    }

    val callPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openDialer(useDirectCall = true)
        else openDialer(useDirectCall = false)
    }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val playable = withContext(Dispatchers.IO) { AudioFiles.isPlayable(context, uri) }
                if (!playable) {
                    Toast.makeText(context, "This file does not contain playable audio.", Toast.LENGTH_LONG).show()
                } else {
                    val old = voiceUri
                    AudioFiles.persist(context, uri)
                    voiceUri = uri.toString()
                    voiceLabel = AudioFiles.displayName(context, uri) ?: "Selected audio"
                    if (old != null && old != uri.toString()) AudioFiles.release(context, old)
                    player.release()
                }
            }
        }
    }

    fun playClip() {
        val selected = voiceUri
        if (selected == null) {
            Toast.makeText(context, "Choose an audio clip first.", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            when (playerState) {
                VoiceState.Playing -> player.pause()
                VoiceState.Ready, VoiceState.Paused -> player.play()
                VoiceState.Finished -> player.replay()
                VoiceState.NoClip, is VoiceState.Error -> {
                    val loaded = withContext(Dispatchers.IO) { player.load(selected, repeat = false) }
                    if (loaded) player.play()
                    else Toast.makeText(context, (player.state.value as? VoiceState.Error)?.message ?: "Can't play this file.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar("Outgoing SIM call", onBack = { nav.popBackStack() }) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Place a real cellular call using your SIM. This is separate from CallStudio's simulated incoming calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = number,
                onValueChange = { number = it; dialerOpened = false; simCallStarted = false },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Phone number") },
                placeholder = { Text("e.g. +91 98765 43210") },
                singleLine = true,
            )

            Card {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Audio for this call", style = MaterialTheme.typography.titleMedium)
                    Text(
                        voiceLabel ?: "No audio selected. This call will be silent unless you choose a clip.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                            Text(if (voiceUri == null) "Choose MP3 / audio" else "Change audio")
                        }
                        if (voiceUri != null) {
                            TextButton(onClick = {
                                val old = voiceUri
                                voiceUri = null
                                voiceLabel = null
                                player.release()
                                AudioFiles.release(context, old)
                            }) { Text("Remove") }
                        }
                    }
                    if (voiceUri != null) {
                        Button(onClick = { playClip() }, modifier = Modifier.fillMaxWidth()) {
                            Icon(
                                if (playerState == VoiceState.Playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = null,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when {
                                    playerState == VoiceState.Playing -> "Pause clip"
                                    dialerOpened -> "Play clip on this phone"
                                    else -> "Preview clip on this phone"
                                },
                            )
                        }
                        if (playerState is VoiceState.Error) {
                            Text((playerState as VoiceState.Error).message, color = MaterialTheme.colorScheme.error)
                        }
                        if (playerState == VoiceState.Finished) {
                            Text("Clip finished. Tap play to replay it.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                        openDialer(useDirectCall = true)
                    } else {
                        callPermission.launch(Manifest.permission.CALL_PHONE)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Call using SIM")
            }
            OutlinedButton(onClick = { openDialer(useDirectCall = false) }, modifier = Modifier.fillMaxWidth()) {
                Text("Open dialer instead")
            }

            if (dialerOpened) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            if (simCallStarted) "SIM call handed to the phone app" else "System dialer opened",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            if (simCallStarted) {
                                "After the other person answers, turn on speakerphone in the phone app, return to CallStudio, then tap Play clip on this phone."
                            } else {
                                "Tap the call button in the dialer to place the SIM call. After the other person answers, enable speakerphone, return here and play the clip."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = {
                            val old = voiceUri
                            player.release()
                            if (old != null) AudioFiles.release(context, old)
                            number = ""
                            voiceUri = null
                            voiceLabel = null
                            dialerOpened = false
                            simCallStarted = false
                        }) { Text("Prepare another call") }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.VolumeUp, contentDescription = null)
                        Text("Important audio limitation", style = MaterialTheme.typography.titleSmall)
                    }
                    Text(
                        "The MP3 plays on this phone; Android does not let a normal app send an MP3 directly into a SIM call's outgoing audio. With speakerphone on, the phone microphone may pick up the sound, but echo cancellation, routing and device restrictions can prevent the other person from hearing it. CallStudio cannot reliably detect the exact moment the other person answers or start playback automatically.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
