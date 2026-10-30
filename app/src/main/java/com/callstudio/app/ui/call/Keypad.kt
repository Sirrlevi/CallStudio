package com.callstudio.app.ui.call

import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A working dial pad: each key plays its DTMF tone through the call audio on this device. */
@Composable
fun KeypadDialog(onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf("") }
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_VOICE_CALL, 80) }.getOrNull() }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(typed.ifEmpty { "Keypad" }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        row.forEach { key ->
                            FilledTonalButton(
                                onClick = {
                                    typed = (typed + key).takeLast(24)
                                    val code = when (key) {
                                        "*" -> ToneGenerator.TONE_DTMF_S
                                        "#" -> ToneGenerator.TONE_DTMF_P
                                        else -> key.toInt()
                                    }
                                    tone?.startTone(code, 150)
                                },
                                modifier = Modifier.width(76.dp).height(56.dp).padding(horizontal = 0.dp),
                            ) { Text(key, fontSize = 22.sp) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (typed.isNotEmpty()) TextButton(onClick = { typed = typed.dropLast(1) }) { Text("Delete") }
        },
    )
}
