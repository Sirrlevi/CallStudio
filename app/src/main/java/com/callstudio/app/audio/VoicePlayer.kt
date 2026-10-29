package com.callstudio.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.FileNotFoundException

sealed interface VoiceState {
    data object NoClip : VoiceState
    data object Ready : VoiceState
    data object Playing : VoiceState
    data object Paused : VoiceState
    data object Finished : VoiceState
    data class Error(val message: String) : VoiceState
}

/**
 * Plays the caller's voice clip locally on this device. It finishing never ends the call; the session owner
 * decides that. With [inCall] the clip uses the voice-call stream so earpiece/speaker routing applies.
 */
class VoicePlayer(private val context: Context, private val inCall: Boolean) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val _state = MutableStateFlow<VoiceState>(VoiceState.NoClip)
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private var mp: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var resumeOnGain = false

    private val attrs = AudioAttributes.Builder()
        .setUsage(if (inCall) AudioAttributes.USAGE_VOICE_COMMUNICATION else AudioAttributes.USAGE_MEDIA)
        .setContentType(if (inCall) AudioAttributes.CONTENT_TYPE_SPEECH else AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        synchronized(this) {
            val p = mp ?: return@synchronized
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    if (_state.value == VoiceState.Playing) {
                        runCatching { p.pause() }
                        _state.value = VoiceState.Paused
                    }
                    resumeOnGain = false
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    if (_state.value == VoiceState.Playing) {
                        runCatching { p.pause() }
                        _state.value = VoiceState.Paused
                        resumeOnGain = true
                    }
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> runCatching { p.setVolume(0.25f, 0.25f) }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    runCatching { p.setVolume(1f, 1f) }
                    if (resumeOnGain && _state.value == VoiceState.Paused) {
                        resumeOnGain = false
                        runCatching { p.start() }
                        _state.value = VoiceState.Playing
                    }
                }
            }
        }
    }

    /** Blocking (prepares the file); call from a background dispatcher. Returns true when playable. */
    @Synchronized
    fun load(uriString: String?, repeat: Boolean): Boolean {
        releasePlayer()
        if (uriString.isNullOrBlank()) {
            _state.value = VoiceState.NoClip
            return false
        }
        val player = MediaPlayer()
        return try {
            player.setAudioAttributes(attrs)
            player.setDataSource(context, Uri.parse(uriString))
            player.isLooping = repeat
            player.setOnCompletionListener { onCompleted() }
            player.setOnErrorListener { _, _, _ ->
                onFailed(UNSUPPORTED)
                true
            }
            player.prepare()
            mp = player
            _state.value = VoiceState.Ready
            true
        } catch (e: Exception) {
            runCatching { player.release() }
            _state.value = VoiceState.Error(
                if (e is FileNotFoundException || e is SecurityException) MISSING else UNSUPPORTED
            )
            false
        }
    }

    @Synchronized
    fun play() {
        val p = mp ?: return
        if (_state.value == VoiceState.Playing) return
        requestFocus()
        try {
            p.start()
            _state.value = VoiceState.Playing
        } catch (e: IllegalStateException) {
            onFailed(UNSUPPORTED)
        }
    }

    @Synchronized
    fun pause() {
        val p = mp ?: return
        if (_state.value == VoiceState.Playing) {
            runCatching { p.pause() }
            _state.value = VoiceState.Paused
        }
        resumeOnGain = false
    }

    @Synchronized
    fun replay() {
        val p = mp ?: return
        runCatching { p.seekTo(0) }
        _state.value = VoiceState.Ready
        play()
    }

    @Synchronized
    fun release() {
        releasePlayer()
        _state.value = VoiceState.NoClip
    }

    @Synchronized
    private fun onCompleted() {
        _state.value = VoiceState.Finished
        abandonFocus()
    }

    @Synchronized
    private fun onFailed(message: String) {
        releasePlayer()
        _state.value = VoiceState.Error(message)
    }

    private fun releasePlayer() {
        mp?.let { runCatching { it.release() } }
        mp = null
        resumeOnGain = false
        abandonFocus()
    }

    private fun requestFocus() {
        if (focus != null) return
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        focus = req
        audio.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    companion object {
        const val MISSING = "The audio file isn't available any more. Choose it again in Audio settings."
        const val UNSUPPORTED = "This audio file can't be played. Try an MP3, M4A, WAV, OGG or FLAC file."
    }
}
