package com.callstudio.app.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri

object RingtonePreviewPlayer {
    private var active: Ringtone? = null

    @Synchronized
    fun toggle(context: Context, uriText: String?): Boolean {
        if (active?.isPlaying == true) {
            stop()
            return false
        }
        stop()
        return try {
            val uri = uriText?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            active = RingtoneManager.getRingtone(context.applicationContext, uri)?.apply {
                audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                play()
            }
            active?.isPlaying == true
        } catch (_: Exception) { false }
    }

    @Synchronized
    fun stop() {
        try { active?.stop() } catch (_: Exception) { }
        active = null
    }
}
