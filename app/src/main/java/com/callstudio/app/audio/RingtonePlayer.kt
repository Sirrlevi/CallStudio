package com.callstudio.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.callstudio.app.preferences.RingtoneSource

/**
 * Plays the incoming-call ringtone and vibration. Kept separate from caller-voice playback.
 * Respects the phone's ringer mode: in silent mode nothing sounds, in vibrate mode only vibration runs.
 */
class RingtonePlayer(private val context: Context) {
    enum class Result { PLAYING, SILENT_MODE, NO_SOUND, FAILED }

    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Blocking (prepares the file); call from a background dispatcher. */
    @Synchronized
    fun start(source: RingtoneSource, uri: String?, vibrate: Boolean, loop: Boolean): Result {
        stop()
        val ringerMode = audio.ringerMode
        if (vibrate && ringerMode != AudioManager.RINGER_MODE_SILENT) startVibration(loop)
        if (source == RingtoneSource.SILENT) return Result.NO_SOUND
        if (ringerMode != AudioManager.RINGER_MODE_NORMAL) return Result.SILENT_MODE
        requestFocus()
        for (candidate in candidates(source, uri)) {
            if (tryPlay(candidate, loop)) return Result.PLAYING
        }
        abandonFocus()
        return Result.FAILED
    }

    @Synchronized
    fun stop() {
        player?.let { p ->
            runCatching { if (p.isPlaying) p.stop() }
            runCatching { p.release() }
        }
        player = null
        runCatching { vibrator?.cancel() }
        vibrator = null
        abandonFocus()
    }

    private fun candidates(source: RingtoneSource, uri: String?): List<Uri> {
        val list = mutableListOf<Uri>()
        if ((source == RingtoneSource.SYSTEM || source == RingtoneSource.CUSTOM) && uri != null) list.add(Uri.parse(uri))
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) }
            .getOrNull()?.let { list.add(it) }
        list.add(Settings.System.DEFAULT_RINGTONE_URI)
        return list
    }

    private fun tryPlay(uri: Uri, loop: Boolean): Boolean {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.isLooping = loop
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            false
        }
    }

    private fun requestFocus() {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
        focus = req
        audio.requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focus?.let { audio.abandonAudioFocusRequest(it) }
        focus = null
    }

    @Suppress("DEPRECATION")
    private fun vibratorService(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    @Suppress("DEPRECATION")
    private fun startVibration(loop: Boolean) {
        val v = vibratorService() ?: return
        if (!v.hasVibrator()) return
        vibrator = v
        val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 700), if (loop) 0 else -1)
        v.vibrate(effect, attrs)
    }
}
