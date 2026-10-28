package com.callstudio.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build

object AudioClipPlayer {
    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var audioManager: AudioManager? = null

    @Synchronized
    fun play(context: Context, uri: Uri, repeat: Boolean) {
        stop()
        val appContext = context.applicationContext
        audioManager = appContext.getSystemService(AudioManager::class.java)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
                focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(attrs).setOnAudioFocusChangeListener { change ->
                        if (change <= AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
                    }.build()
                audioManager?.requestAudioFocus(focusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager?.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            }
            val newPlayer = MediaPlayer()
            player = newPlayer
            newPlayer.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            )
            newPlayer.isLooping = repeat
            newPlayer.setDataSource(appContext, uri)
            newPlayer.setOnPreparedListener { mp -> try { mp.start() } catch (_: IllegalStateException) { } }
            newPlayer.setOnErrorListener { mp, _, _ ->
                try { mp.release() } catch (_: Exception) { }
                synchronized(this) { if (player === mp) player = null }
                true
            }
            newPlayer.prepareAsync()
        } catch (_: Exception) {
            stop()
        }
    }

    @Synchronized fun pause() { try { if (player?.isPlaying == true) player?.pause() } catch (_: Exception) { } }
    @Synchronized fun resume() { try { player?.start() } catch (_: Exception) { } }
    @Synchronized fun togglePause() { try { if (player?.isPlaying == true) player?.pause() else player?.start() } catch (_: Exception) { } }

    @Synchronized
    fun stop() {
        try { player?.stop() } catch (_: Exception) { }
        try { player?.release() } catch (_: Exception) { }
        player = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }
            else {
                @Suppress("DEPRECATION")
                audioManager?.abandonAudioFocus(null)
            }
        } catch (_: Exception) { }
        focusRequest = null
        audioManager = null
    }
}
