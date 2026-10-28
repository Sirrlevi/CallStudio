package com.callstudio.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.callstudio.app.CallAlarmReceiver
import com.callstudio.app.data.ScheduledCall

object CallScheduler {
    fun schedule(context: Context, call: ScheduledCall): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        val pending = alarmIntent(context, call.id)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, call.triggerAtMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, call.triggerAtMillis, pending)
            }
            true
        } catch (_: SecurityException) {
            try {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, call.triggerAtMillis, pending)
                true
            } catch (_: Exception) { false }
        } catch (_: Exception) { false }
    }

    fun cancel(context: Context, callId: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context, callId)
        alarmManager.cancel(pending)
        pending.cancel()
    }

    private fun alarmIntent(context: Context, callId: Long): PendingIntent {
        val intent = Intent(context, CallAlarmReceiver::class.java).apply {
            action = "com.callstudio.app.SCHEDULED_CALL"
            data = Uri.parse("callstudio://scheduled/$callId")
            putExtra(CallExtras.EXTRA_SCHEDULED_ID, callId)
        }
        return PendingIntent.getBroadcast(
            context, (callId % 2_000_000L).toInt() + 200,
            intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

object CallExtras {
    const val EXTRA_SCHEDULED_ID = "scheduled_id"
    const val EXTRA_CALLER_ID = "caller_id"
    const val EXTRA_NAME = "caller_name"
    const val EXTRA_PHONE = "caller_phone"
    const val EXTRA_PHOTO_URI = "caller_photo_uri"
    const val EXTRA_VOICE_URI = "caller_voice_uri"
    const val EXTRA_REPEAT_VOICE = "repeat_voice"
    const val EXTRA_ACTIVE = "active_call"
    const val EXTRA_START_TIME = "call_start_time"
    const val ACTION_STOP_RINGING = "com.callstudio.app.STOP_RINGING"
    const val ACTION_DECLINE = "com.callstudio.app.DECLINE_CALL"
    const val NOTIFICATION_ID_BASE = 4300
    fun notificationId(id: Long): Int = NOTIFICATION_ID_BASE + (id % 100_000L).toInt()
}
