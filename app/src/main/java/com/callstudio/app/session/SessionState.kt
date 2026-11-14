package com.callstudio.app.session

import com.callstudio.app.audio.VoiceState
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.recording.RecorderState

enum class MessageAction { NONE, OPEN_APP_SETTINGS }

data class UiMessage(val text: String, val action: MessageAction = MessageAction.NONE)

/** Everything the call screens and the foreground notification need to know about the live call. */
data class SessionState(
    val eventId: Long = -1L,
    val callerId: Long? = null,
    val status: CallStatus? = null,
    val callerName: String = "",
    val callerPhone: String = "",
    val callerPhotoFile: String? = null,
    val callerCountry: String? = null,
    /** Voice clip chosen for this particular call (null = no clip). */
    val voiceUri: String? = null,
    val voiceLabel: String? = null,
    val held: Boolean = false,
    val answeredAtMillis: Long = 0L,
    /** The clock base of the on-screen timer: it shows `elapsedRealtime() - answeredElapsed` (see "Ongoing call timer"). */
    val answeredElapsed: Long = 0L,
    /** SystemClock.elapsedRealtime() when the call was really answered, so a normal timer can always be restored. */
    val answeredRealElapsed: Long = 0L,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val voice: VoiceState = VoiceState.NoClip,
    val recording: RecorderState = RecorderState.Idle,
    val message: UiMessage? = null,
) {
    val isLive: Boolean get() = status == CallStatus.RINGING || status == CallStatus.ACTIVE
    val displayName: String get() = callerName.ifBlank { callerPhone }
}
