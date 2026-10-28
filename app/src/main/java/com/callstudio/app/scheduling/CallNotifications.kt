package com.callstudio.app.scheduling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.callstudio.app.IncomingCallActivity
import com.callstudio.app.RingingService

object CallNotifications {
    const val CHANNEL_ID = "callstudio_incoming_calls"

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Incoming calls", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Scheduled local incoming-call experiences"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(null, null) // RingingService owns ringtone playback.
                enableVibration(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildIncoming(
        context: Context,
        scheduledId: Long,
        callerId: Long,
        name: String,
        phone: String,
        photoUri: String?,
        voiceUri: String?,
        repeatVoice: Boolean
    ): Notification {
        createChannels(context)
        val fullScreen = activityIntent(context, scheduledId, callerId, name, phone, photoUri, voiceUri, repeatVoice, active = false, codeOffset = 1)
        val answer = activityIntent(context, scheduledId, callerId, name, phone, photoUri, voiceUri, repeatVoice, active = true, codeOffset = 2)
        val declineIntent = Intent(context, CallActionReceiver::class.java).apply {
            action = CallExtras.ACTION_DECLINE
            data = android.net.Uri.parse("callstudio://decline/$scheduledId")
            putExtra(CallExtras.EXTRA_SCHEDULED_ID, scheduledId)
            putExtra(CallExtras.EXTRA_NAME, name)
            putExtra(CallExtras.EXTRA_PHONE, phone)
            putExtra(CallExtras.EXTRA_CALLER_ID, callerId)
        }
        val decline = PendingIntent.getBroadcast(
            context, CallExtras.notificationId(scheduledId) + 3, declineIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(name.ifBlank { "Incoming call" })
            .setContentText("Mobile $phone")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .addAction(android.R.drawable.sym_action_call, "Answer", answer)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Decline", decline)
            .build()
    }

    fun postFallback(context: Context, notification: Notification, scheduledId: Long) {
        try {
            context.getSystemService(NotificationManager::class.java).notify(CallExtras.notificationId(scheduledId), notification)
        } catch (_: SecurityException) { }
    }

    fun cancel(context: Context, scheduledId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(CallExtras.notificationId(scheduledId))
    }

    private fun activityIntent(
        context: Context, scheduledId: Long, callerId: Long, name: String, phone: String,
        photoUri: String?, voiceUri: String?, repeatVoice: Boolean, active: Boolean, codeOffset: Int
    ): PendingIntent {
        val intent = Intent(context, IncomingCallActivity::class.java).apply {
            data = android.net.Uri.parse("callstudio://incoming/$scheduledId/$codeOffset")
            putExtra(CallExtras.EXTRA_SCHEDULED_ID, scheduledId)
            putExtra(CallExtras.EXTRA_CALLER_ID, callerId)
            putExtra(CallExtras.EXTRA_NAME, name)
            putExtra(CallExtras.EXTRA_PHONE, phone)
            putExtra(CallExtras.EXTRA_PHOTO_URI, photoUri)
            putExtra(CallExtras.EXTRA_VOICE_URI, voiceUri)
            putExtra(CallExtras.EXTRA_REPEAT_VOICE, repeatVoice)
            putExtra(CallExtras.EXTRA_ACTIVE, active)
        }
        return PendingIntent.getActivity(
            context, CallExtras.notificationId(scheduledId) + codeOffset, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
