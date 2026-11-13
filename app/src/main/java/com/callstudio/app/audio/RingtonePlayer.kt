package com.callstudio.app.audio

import android.app.NotificationManager
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
import android.util.Log
import com.callstudio.app.preferences.RingtoneSource

/**
 * What an incoming call should do right now. Pure on purpose, so the rules can be unit-tested:
 * silent = nothing, vibrate = vibration only, normal = ringtone (plus vibration when the phone's
 * "also vibrate for calls" is on).
 */
object RingPolicy {
    enum class Mode { SILENT, VIBRATE, NORMAL }

    data class Plan(val vibrate: Boolean, val sound: Boolean)

    /**
     * @param followPhone  copy the phone's own behaviour (the default). When false the app's own vibration switch decides.
     * @param appVibrate   the app's own vibration switch.
     * @param phoneVibratesWhenRinging the phone's "also vibrate for calls" setting, or null when this phone doesn't expose it.
     * @param ringtoneSilent the app's ringtone is set to "silent".
     */
    fun plan(
        mode: Mode,
        followPhone: Boolean,
        appVibrate: Boolean,
        phoneVibratesWhenRinging: Boolean?,
        ringtoneSilent: Boolean,
    ): Plan {
        val vibrate = when {
            mode == Mode.SILENT -> false
            // Same as a real call: a phone on vibrate always vibrates for incoming calls.
            followPhone && mode == Mode.VIBRATE -> true
            followPhone -> phoneVibratesWhenRinging ?: appVibrate
            else -> appVibrate
        }
        return Plan(vibrate = vibrate, sound = mode == Mode.NORMAL && !ringtoneSilent)
    }
}

/**
 * Plays the incoming-call ringtone and vibration. Kept separate from caller-voice playback.
 * Follows the phone's ringer mode like a real call (see [RingPolicy]).
 */
class RingtonePlayer(private val context: Context) {
    enum class Result { PLAYING, SILENT_MODE, NO_SOUND, FAILED }

    private val audio = context.getSystemService(AudioManager::class.java)
    private var player: MediaPlayer? = null
    private var focus: AudioFocusRequest? = null
    private var vibrator: Vibrator? = null

    /** True while the vibration started by the last [start] is (believed to be) running. */
    @Volatile
    var vibrating: Boolean = false
        private set

    /** One line about what the last [start] did and why; shown in the call report. */
    @Volatile
    var lastReport: String = ""
        private set

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    /** Blocking (prepares the file); call from a background dispatcher. Never throws. */
    @Synchronized
    fun start(
        source: RingtoneSource,
        uri: String?,
        vibrate: Boolean,
        loop: Boolean,
        followPhone: Boolean = true,
    ): Result {
        stop()
        val mode = currentMode()
        val plan = RingPolicy.plan(
            mode = mode,
            followPhone = followPhone,
            appVibrate = vibrate,
            phoneVibratesWhenRinging = if (followPhone) phoneVibratesWhenRinging() else null,
            ringtoneSilent = source == RingtoneSource.SILENT,
        )
        val vibrationOk = plan.vibrate && startVibration(loop)
        vibrating = vibrationOk
        val head = "ringer=$mode dnd=${interruptionFilter()} vibrate=${if (plan.vibrate) (if (vibrationOk) "on" else "FAILED") else "off"}"

        if (!plan.sound) {
            lastReport = "$head sound=off"
            return if (source == RingtoneSource.SILENT) Result.NO_SOUND else Result.SILENT_MODE
        }
        requestFocus()
        val failures = mutableListOf<String>()
        for (candidate in candidates(source, uri)) {
            val error = tryPlay(candidate, loop)
            if (error == null) {
                lastReport = "$head sound=playing"
                return Result.PLAYING
            }
            failures += "${candidate.scheme}:$error"
        }
        abandonFocus()
        lastReport = "$head sound=FAILED(${failures.joinToString(",")})"
        Log.w(TAG, "No ringtone could be played: $lastReport")
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
        vibrating = false
        abandonFocus()
    }

    private fun currentMode(): RingPolicy.Mode = when (runCatching { audio.ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)) {
        AudioManager.RINGER_MODE_SILENT -> RingPolicy.Mode.SILENT
        AudioManager.RINGER_MODE_VIBRATE -> RingPolicy.Mode.VIBRATE
        else -> RingPolicy.Mode.NORMAL
    }

    /** Do Not Disturb state as a short word, for the call report only. */
    private fun interruptionFilter(): String {
        val filter = runCatching {
            context.getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        }.getOrNull()
        return when (filter) {
            NotificationManager.INTERRUPTION_FILTER_ALL -> "off"
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms-only"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "total-silence"
            else -> "unknown"
        }
    }

    /** The phone's own "vibrate when ringing / also vibrate for calls" switch, or null if this phone doesn't expose it. */
    private fun phoneVibratesWhenRinging(): Boolean? {
        val value = runCatching {
            // The constant is hidden in the SDK, but the setting itself is readable by any app.
            Settings.System.getInt(context.contentResolver, "vibrate_when_ringing", -1)
        }.getOrDefault(-1)
        return if (value < 0) null else value != 0
    }

    private fun candidates(source: RingtoneSource, uri: String?): List<Uri> {
        val list = mutableListOf<Uri>()
        if ((source == RingtoneSource.SYSTEM || source == RingtoneSource.CUSTOM) && uri != null) {
            runCatching { Uri.parse(uri) }.getOrNull()?.let { list.add(it) }
        }
        runCatching { RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE) }
            .getOrNull()?.let { list.add(it) }
        list.add(Settings.System.DEFAULT_RINGTONE_URI)
        return list
    }

    /** Returns null when [uri] is now playing, otherwise the name of what went wrong. */
    private fun tryPlay(uri: Uri, loop: Boolean): String? {
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(attrs)
            mp.setDataSource(context, uri)
            mp.isLooping = loop
            mp.prepare()
            mp.start()
            player = mp
            null
        } catch (e: Exception) {
            runCatching { mp.release() }
            e.javaClass.simpleName
        }
    }

    private fun requestFocus() {
        runCatching {
            val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).build()
            focus = req
            audio.requestAudioFocus(req)
        }
    }

    private fun abandonFocus() {
        focus?.let { request -> runCatching { audio.abandonAudioFocusRequest(request) } }
        focus = null
    }

    @Suppress("DEPRECATION")
    private fun vibratorService(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    /** Returns true when the vibration was handed to the system. */
    @Suppress("DEPRECATION")
    private fun startVibration(loop: Boolean): Boolean {
        return try {
            val v = vibratorService() ?: return false
            if (!v.hasVibrator()) return false
            vibrator = v
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 700, 700), if (loop) 0 else -1)
            v.vibrate(effect, attrs)
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not vibrate", e)
            false
        }
    }

    private companion object {
        const val TAG = "RingtonePlayer"
    }
}
