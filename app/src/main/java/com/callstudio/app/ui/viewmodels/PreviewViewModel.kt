package com.callstudio.app.ui.viewmodels

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.audio.AudioRouter
import com.callstudio.app.audio.RingtonePlayer
import com.callstudio.app.container
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.Countries
import com.callstudio.app.preferences.AppSettings
import com.callstudio.app.ui.call.CallerUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Previews the real thing with the saved caller, theme and audio settings: the actual ringtone while ringing and
 * real speaker routing once answered. Nothing is recorded or scheduled. (Voice clips belong to individual calls,
 * so they play in real calls, not here.)
 */
class PreviewViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ringtone = RingtonePlayer(app)
    private val router = AudioRouter(app)

    val settings: StateFlow<AppSettings?> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val liveCall = c.sessions.session

    var caller by mutableStateOf<CallerUi?>(null)
        private set
    var speakerOn by mutableStateOf(false)
        private set
    var inCall by mutableStateOf(false)
        private set
    private var ringing = false

    fun loadCaller(id: Long) {
        if (caller != null) return
        viewModelScope.launch {
            val chosen = (if (id > 0) c.callers.get(id) else null)
                ?: c.callers.get(c.settings.current().lastCallerId)
                ?: c.callers.firstOrNull()
            caller = chosen?.let { CallerUi(it.name, it.displayNumber(), it.photoFile, Countries.nameOf(it.countryIso)) }
                ?: CallerUi("Alex Morgan", "+1 555 010 0100", null, "United States")
        }
    }

    fun startRinging() {
        if (inCall || ringing || c.sessions.session.value.isLive) return
        ringing = true
        viewModelScope.launch {
            val st = c.settings.current()
            withContext(Dispatchers.IO) { ringtone.start(st.ringtoneSource, st.ringtoneUri, st.vibrate, loop = true) }
            if (!ringing) ringtone.stop()
        }
    }

    fun stopRinging() {
        ringing = false
        ringtone.stop()
    }

    fun answer() {
        if (inCall) return
        stopRinging()
        inCall = true
        router.enterCall()
        viewModelScope.launch {
            val st = c.settings.current()
            if (st.startOnSpeaker) router.setSpeaker(true)
            speakerOn = router.isSpeakerOn()
        }
    }

    fun toggleSpeaker() {
        router.setSpeaker(!speakerOn)
        speakerOn = router.isSpeakerOn()
    }

    fun finish() {
        stopRinging()
        if (inCall) router.exitCall()
        inCall = false
        speakerOn = false
    }

    override fun onCleared() = finish()
}
