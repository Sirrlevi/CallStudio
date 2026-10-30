package com.callstudio.app.ui.call

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.ui.graphics.vector.ImageVector
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.recording.RecorderState
import com.callstudio.app.session.SessionState

data class CallerUi(val name: String, val phone: String, val photoFile: String?, val country: String? = null) {
    val title: String get() = name.ifBlank { phone }.ifBlank { "Unknown" }
}

class IncomingActions(val onAnswer: () -> Unit, val onDecline: () -> Unit, val onMessage: () -> Unit = {})

data class ControlSpec(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val active: Boolean = false,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

data class InCallModel(
    val caller: CallerUi,
    val elapsedMs: Long,
    val recordingLabel: String?,
    val controls: List<ControlSpec>,
    val onEnd: () -> Unit,
    /** Keypad, hold and message; only themes whose layout has slots for them show these. */
    val extraControls: List<ControlSpec> = emptyList(),
    val held: Boolean = false,
)

class CallActions(
    val onAnswer: () -> Unit,
    val onDecline: () -> Unit,
    val onMute: () -> Unit,
    val onSpeaker: () -> Unit,
    val onRecord: () -> Unit,
    val onRecordPause: () -> Unit,
    val onVoice: () -> Unit,
    val onEnd: () -> Unit,
    val onHold: () -> Unit = {},
    val onMessage: () -> Unit = {},
)

fun recordingLabel(state: RecorderState): String? = when (state) {
    is RecorderState.Recording -> "Recording ${TimeFormat.elapsed(state.elapsedMs)}"
    is RecorderState.Paused -> "Recording paused ${TimeFormat.elapsed(state.elapsedMs)}"
    else -> null
}

/** Every control maps to a real action in the session manager; there are no decorative buttons. */
fun buildControls(s: SessionState, a: CallActions): List<ControlSpec> {
    val list = mutableListOf<ControlSpec>()
    list += ControlSpec(
        "mute", if (s.muted) "Unmute" else "Mute",
        if (s.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
        active = s.muted, onClick = a.onMute,
    )
    list += ControlSpec("speaker", "Speaker", Icons.Filled.VolumeUp, active = s.speakerOn, onClick = a.onSpeaker)
    if (s.recording.isActive) {
        list += ControlSpec("record", "Stop rec", Icons.Filled.Stop, active = true, onClick = a.onRecord)
        val paused = s.recording is RecorderState.Paused
        list += ControlSpec(
            "pauseRec", if (paused) "Resume rec" else "Pause rec",
            if (paused) Icons.Filled.PlayArrow else Icons.Filled.Pause, onClick = a.onRecordPause,
        )
    } else {
        list += ControlSpec("record", "Record", Icons.Filled.FiberManualRecord, onClick = a.onRecord)
    }
    voiceControl(s.voice, a.onVoice)?.let { list += it }
    return list
}

/** Shared by the real call and the preview so both offer the same clip controls. */
fun voiceControl(state: VoiceState, onClick: () -> Unit): ControlSpec? = when (state) {
    VoiceState.NoClip -> null
    VoiceState.Playing -> ControlSpec("voice", "Pause clip", Icons.Filled.Pause, active = true, onClick = onClick)
    VoiceState.Paused -> ControlSpec("voice", "Resume clip", Icons.Filled.PlayArrow, onClick = onClick)
    VoiceState.Ready -> ControlSpec("voice", "Play clip", Icons.Filled.PlayArrow, onClick = onClick)
    VoiceState.Finished -> ControlSpec("voice", "Replay clip", Icons.Filled.Replay, onClick = onClick)
    is VoiceState.Error -> ControlSpec("voice", "Retry clip", Icons.Filled.Refresh, onClick = onClick)
}
