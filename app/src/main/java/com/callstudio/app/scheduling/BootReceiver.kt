package com.callstudio.app.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callstudio.app.CallStudioApp
import kotlinx.coroutines.launch

/**
 * Re-arms scheduled calls after reboot, app update, clock/time-zone change, or a change in exact-alarm access.
 * Not direct-boot aware: it runs after the first unlock, when the database is readable.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recompute = when (intent.action) {
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED -> true
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> false
            else -> return
        }
        val container = (context.applicationContext as CallStudioApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.sessions.awaitReady()
                container.coordinator.recover(recomputeWallClock = recompute)
            } finally {
                pending.finish()
            }
        }
    }
}
