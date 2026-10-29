package com.callstudio.app.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Earpiece/speaker routing for the call session. Restores the previous audio mode on exit. */
class AudioRouter(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private var previousMode: Int? = null

    fun enterCall() {
        if (previousMode == null) previousMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION
    }

    fun exitCall() {
        clearRoute()
        previousMode?.let { am.mode = it }
        previousMode = null
    }

    /** Returns false when the device refused or has no matching output. */
    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (on) {
                val device = am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    ?: return false
                am.setCommunicationDevice(device)
            } else {
                am.clearCommunicationDevice()
                true
            }
        } else {
            am.isSpeakerphoneOn = on
            am.isSpeakerphoneOn == on
        }
    }

    /** Reads the real route instead of trusting the last request. */
    @Suppress("DEPRECATION")
    fun isSpeakerOn(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.communicationDevice?.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
        } else {
            am.isSpeakerphoneOn
        }

    @Suppress("DEPRECATION")
    private fun clearRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) am.clearCommunicationDevice() else am.isSpeakerphoneOn = false
    }
}
