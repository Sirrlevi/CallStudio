package com.callstudio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callstudio.app.data.CallHistory
import com.callstudio.app.data.CallStudioDatabase
import com.callstudio.app.scheduling.CallExtras
import com.callstudio.app.scheduling.CallNotifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != CallExtras.ACTION_DECLINE) return
        val id = intent.getLongExtra(CallExtras.EXTRA_SCHEDULED_ID, 0L)
        val name = intent.getStringExtra(CallExtras.EXTRA_NAME).orEmpty()
        val phone = intent.getStringExtra(CallExtras.EXTRA_PHONE).orEmpty()
        RingingService.stop(context, id)
        CallNotifications.cancel(context, id)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = CallStudioDatabase.get(context)
                if (id > 0) db.scheduledCallDao().updateState(id, "DECLINED")
                db.callHistoryDao().insert(
                    CallHistory(
                        callerName = name,
                        phone = phone,
                        outcome = "Declined",
                        startedAtMillis = System.currentTimeMillis(),
                        durationSeconds = 0,
                        scheduledCallId = id.takeIf { it > 0 }
                    )
                )
            } finally { pendingResult.finish() }
        }
    }
}
