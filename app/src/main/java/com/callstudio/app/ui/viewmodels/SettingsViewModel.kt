package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.audio.AudioFiles
import com.callstudio.app.audio.RingtonePlayer
import com.callstudio.app.container
import com.callstudio.app.preferences.AppSettings
import com.callstudio.app.preferences.EdgeGlowStyle
import com.callstudio.app.preferences.NotificationBadgeStyle
import com.callstudio.app.preferences.RingtoneSource
import com.callstudio.app.preferences.VoicePlayMode
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ctx: Application = app

    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val liveCall = c.sessions.session

    private val ringPreview = RingtonePlayer(app)

    var ringPreviewing by mutableStateOf(false)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun clearNotice() { notice = null }

    fun setTheme(theme: CallTheme) { viewModelScope.launch { c.settings.setTheme(theme) } }
    fun setVibrate(on: Boolean) { viewModelScope.launch { c.settings.setVibrate(on) } }
    fun setFollowPhoneRinger(on: Boolean) { viewModelScope.launch { c.settings.setFollowPhoneRinger(on) } }
    fun setEdgeGlow(style: EdgeGlowStyle) { viewModelScope.launch { c.settings.setEdgeGlow(style) } }
    fun setVoiceMode(mode: VoicePlayMode) { viewModelScope.launch { c.settings.setVoiceMode(mode) } }
    fun setStartOnSpeaker(on: Boolean) { viewModelScope.launch { c.settings.setStartOnSpeaker(on) } }
    fun setKeepScreenOn(on: Boolean) { viewModelScope.launch { c.settings.setKeepScreenOn(on) } }
    fun setRingTimeout(seconds: Int) { viewModelScope.launch { c.settings.setRingTimeout(seconds) } }
    fun setSampleRate(hz: Int) { viewModelScope.launch { c.settings.setRecordingSampleRate(hz) } }
    fun setNotificationBadgeStyle(style: NotificationBadgeStyle) { viewModelScope.launch { c.settings.setNotificationBadgeStyle(style) } }
    fun setNotificationBadgeEnabled(on: Boolean) { viewModelScope.launch { c.settings.setNotificationBadgeEnabled(on) } }
    fun setCallTimerOffset(seconds: Long) { viewModelScope.launch { c.settings.setCallTimerOffsetSeconds(seconds) } }

    // ---- ringtone ----------------------------------------------------------------------------------------

    fun useDefaultRingtone() = setRingtone(RingtoneSource.DEFAULT, null, null)
    fun useSilentRingtone() = setRingtone(RingtoneSource.SILENT, null, null)

    fun onSystemRingtonePicked(uri: Uri) {
        val title = AudioFiles.ringtoneTitle(ctx, uri) ?: "System sound"
        setRingtone(RingtoneSource.SYSTEM, uri.toString(), title)
    }

    fun onCustomRingtonePicked(uri: Uri) {
        AudioFiles.persist(ctx, uri)
        setRingtone(RingtoneSource.CUSTOM, uri.toString(), AudioFiles.displayName(ctx, uri) ?: "Custom sound")
    }

    private fun setRingtone(source: RingtoneSource, uri: String?, label: String?) {
        viewModelScope.launch {
            stopRingPreview()
            val old = c.settings.current()
            c.settings.setRingtone(source, uri, label)
            if (old.ringtoneSource == RingtoneSource.CUSTOM && old.ringtoneUri != uri) AudioFiles.release(ctx, old.ringtoneUri)
        }
    }

    fun toggleRingPreview() {
        if (ringPreviewing) {
            stopRingPreview()
            return
        }
        viewModelScope.launch {
            val st = c.settings.current()
            if (c.sessions.session.value.isLive) {
                notice = "A call is in progress."
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                ringPreview.start(st.ringtoneSource, st.ringtoneUri, st.vibrate, loop = true, followPhone = st.followPhoneRinger)
            }
            when (result) {
                RingtonePlayer.Result.PLAYING, RingtonePlayer.Result.NO_SOUND -> {
                    ringPreviewing = true
                    launch {
                        delay(6_000)
                        stopRingPreview()
                    }
                }
                RingtonePlayer.Result.SILENT_MODE -> notice = "Your phone is in silent mode, so the ringtone can't be heard."
                RingtonePlayer.Result.FAILED -> notice = "That sound can't be played. Choose another one."
            }
        }
    }

    fun stopRingPreview() {
        ringPreview.stop()
        ringPreviewing = false
    }

    override fun onCleared() {
        ringPreview.stop()
    }
}
