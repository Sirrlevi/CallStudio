package com.callstudio.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callstudio.app.CallStudioApp
import com.callstudio.app.notifications.NotificationHelper
import kotlinx.coroutines.launch

/** Thin: hands the due event to the session manager, which posts the incoming-call notification. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val id = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        if (id < 0) return
        val container = (context.applicationContext as CallStudioApp).container
        val pending = goAsync()
        container.appScope.launch {
            try {
                container.sessions.onAlarm(id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.callstudio.app.action.FIRE_CALL"
    }
}
