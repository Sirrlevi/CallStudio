package com.callstudio.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callstudio.app.CallStudioApp

/** Notification buttons that need no screen: decline, hang up, mute. Stale taps are ignored by the manager. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        if (id < 0) return
        val sessions = (context.applicationContext as CallStudioApp).container.sessions
        when (intent.action) {
            NotificationHelper.ACTION_DECLINE -> sessions.decline(id)
            NotificationHelper.ACTION_END -> sessions.end(id)
            NotificationHelper.ACTION_TOGGLE_MUTE -> sessions.toggleMute(id)
        }
    }
}
