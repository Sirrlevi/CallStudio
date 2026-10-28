package com.callstudio.app

import android.app.Service
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.callstudio.app.scheduling.CallExtras
import com.callstudio.app.scheduling.CallNotifications

class RingingService : Service() {
    private var ringtone: Ringtone? = null
    private var activeId: Long = 0L
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == CallExtras.ACTION_STOP_RINGING) {
            stopRingingAndSelf()
            return START_NOT_STICKY
        }
        activeId = intent?.getLongExtra(CallExtras.EXTRA_SCHEDULED_ID, 0L) ?: 0L
        val callerId = intent?.getLongExtra(CallExtras.EXTRA_CALLER_ID, 0L) ?: 0L
        val name = intent?.getStringExtra(CallExtras.EXTRA_NAME) ?: "Incoming call"
        val phone = intent?.getStringExtra(CallExtras.EXTRA_PHONE) ?: ""
        val photoUri = intent?.getStringExtra(CallExtras.EXTRA_PHOTO_URI)
        val voiceUri = intent?.getStringExtra(CallExtras.EXTRA_VOICE_URI)
        val repeatVoice = intent?.getBooleanExtra(CallExtras.EXTRA_REPEAT_VOICE, false) ?: false
        val notification = CallNotifications.buildIncoming(
            this, activeId, callerId, name, phone, photoUri, voiceUri, repeatVoice
        )
        try {
            startForeground(CallExtras.notificationId(activeId), notification)
        } catch (e: Exception) {
            CallNotifications.postFallback(this, notification, activeId)
            stopSelf()
            return START_NOT_STICKY
        }
        startRingtone()
        startVibration()
        return START_NOT_STICKY
    }

    private fun startRingtone() {
        try { ringtone?.stop() } catch (_: Exception) { }
        ringtone = null
        try {
            val configured = getSharedPreferences("callstudio_audio", MODE_PRIVATE).getString("ringtone_uri", null)
            val uri = configured?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            ringtone = RingtoneManager.getRingtone(this, uri)
            ringtone?.audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.setLooping(true)
            ringtone?.play()
        } catch (_: Exception) {
            try {
                ringtone = RingtoneManager.getRingtone(this, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) ringtone?.setLooping(true)
                ringtone?.play()
            } catch (_: Exception) { }
        }
    }

    private fun startVibration() {
        val enabled = getSharedPreferences("callstudio_audio", MODE_PRIVATE).getBoolean("vibration", true)
        if (!enabled) return
        try {
            vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 500, 300, 500), 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(longArrayOf(0, 500, 300, 500), 0)
            }
        } catch (_: Exception) { }
    }

    private fun stopRingingAndSelf() {
        try { ringtone?.stop() } catch (_: Exception) { }
        ringtone = null
        try { vibrator?.cancel() } catch (_: Exception) { }
        CallNotifications.cancel(this, activeId)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        try { ringtone?.stop() } catch (_: Exception) { }
        try { vibrator?.cancel() } catch (_: Exception) { }
        ringtone = null
        vibrator = null
        super.onDestroy()
    }

    companion object {
        fun stop(context: android.content.Context, id: Long? = null) {
            val intent = Intent(context, RingingService::class.java).setAction(CallExtras.ACTION_STOP_RINGING)
            if (id != null) intent.putExtra(CallExtras.EXTRA_SCHEDULED_ID, id)
            try { context.startService(intent) } catch (_: Exception) { context.stopService(Intent(context, RingingService::class.java)) }
            if (id != null) CallNotifications.cancel(context, id)
        }
    }
}
