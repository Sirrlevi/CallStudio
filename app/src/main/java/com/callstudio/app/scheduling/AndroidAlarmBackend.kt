package com.callstudio.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.callstudio.app.notifications.NotificationHelper
import com.callstudio.app.notifications.RequestCodes

class AndroidAlarmBackend(private val context: Context) : AlarmBackend {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    override fun canScheduleExact(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    override fun set(eventId: Long, triggerAtMillis: Long, exact: Boolean): Boolean {
        val pi = pending(eventId)
        if (exact) {
            try {
                alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                return true
            } catch (e: SecurityException) {
                // Access was revoked between the check and the call; fall back to an approximate alarm.
            }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        return false
    }

    override fun cancel(eventId: Long) {
        val pi = pending(eventId)
        alarms.cancel(pi)
        pi.cancel()
    }

    private fun pending(eventId: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("callstudio://alarm/$eventId"))
            .putExtra(NotificationHelper.EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(
            context,
            RequestCodes.forEvent(eventId, RequestCodes.ALARM),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
