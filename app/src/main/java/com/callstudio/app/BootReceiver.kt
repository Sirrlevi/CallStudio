package com.callstudio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.callstudio.app.data.CallStudioDatabase
import com.callstudio.app.scheduling.CallScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = CallStudioDatabase.get(context)
                val now = System.currentTimeMillis()
                db.scheduledCallDao().getPending().forEach { item ->
                    if (item.triggerAtMillis > now) CallScheduler.schedule(context, item)
                    else {
                        // Expired events are delivered once as soon as the device is ready.
                        CallScheduler.schedule(context, item.copy(triggerAtMillis = now + 1_000L))
                    }
                }
            } finally { result.finish() }
        }
    }
}
