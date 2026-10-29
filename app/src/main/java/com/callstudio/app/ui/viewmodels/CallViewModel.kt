package com.callstudio.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.container
import com.callstudio.app.preferences.AppSettings
import com.callstudio.app.recording.RecorderState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Thin bridge between the call screen and the session manager (which owns all call state). */
class CallViewModel(app: Application) : AndroidViewModel(app) {
    private val container = app.container
    private val sessions = container.sessions

    val session = sessions.session

    /** Null until the saved theme has loaded, so the screen never flashes the wrong theme. */
    val settings: StateFlow<AppSettings?> =
        container.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun id(): Long = sessions.session.value.eventId

    fun onScreenVisible() = sessions.onCallScreenVisible()
    fun answer() = sessions.answer(id())
    fun answerEvent(eventId: Long) = sessions.answer(eventId)
    fun decline() = sessions.decline(id())
    fun end() = sessions.end(id())
    fun toggleMute() = sessions.toggleMute(id())
    fun toggleSpeaker() = sessions.toggleSpeaker(id())
    fun voiceToggle() = sessions.voiceToggle(id())
    fun startRecording() = sessions.startRecording(id())
    fun stopRecording() = sessions.stopRecording()
    fun micDenied() = sessions.micDenied()
    fun clearMessage() = sessions.clearMessage()

    fun toggleRecordingPause() {
        if (sessions.session.value.recording is RecorderState.Paused) sessions.resumeRecording() else sessions.pauseRecording()
    }
}
