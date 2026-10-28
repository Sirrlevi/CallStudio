package com.callstudio.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.callstudio.app.data.CallStudioDatabase
import com.callstudio.app.scheduling.CallExtras
import com.callstudio.app.scheduling.CallNotifications
import com.callstudio.app.scheduling.CallScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(CallExtras.EXTRA_SCHEDULED_ID, -1L)
        if (id <= 0L) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val db = CallStudioDatabase.get(context)
                val schedule = db.scheduledCallDao().findById(id) ?: return@launch
                if (schedule.state != "SCHEDULED") return@launch
                if (ActiveSessionStore.read(context) != null) {
                    val deferred = schedule.copy(triggerAtMillis = System.currentTimeMillis() + 30_000L)
                    db.scheduledCallDao().update(deferred)
                    CallScheduler.schedule(context, deferred)
                    return@launch
                }
                val caller = db.callerDao().findById(schedule.callerId) ?: run {
                    db.scheduledCallDao().updateState(id, "FAILED")
                    return@launch
                }
                if (db.scheduledCallDao().claimForRinging(id) == 0) return@launch
                val serviceIntent = Intent(context, RingingService::class.java).apply {
                    putExtra(CallExtras.EXTRA_SCHEDULED_ID, id)
                    putExtra(CallExtras.EXTRA_CALLER_ID, caller.id)
                    putExtra(CallExtras.EXTRA_NAME, caller.name)
                    putExtra(CallExtras.EXTRA_PHONE, caller.phone)
                    putExtra(CallExtras.EXTRA_PHOTO_URI, caller.photoUri)
                    putExtra(CallExtras.EXTRA_VOICE_URI, schedule.voiceUri)
                    putExtra(CallExtras.EXTRA_REPEAT_VOICE, schedule.repeatVoice)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) ContextCompat.startForegroundService(context, serviceIntent)
                    else context.startService(serviceIntent)
                } catch (_: Exception) {
                    val notification = CallNotifications.buildIncoming(
                        context, id, caller.id, caller.name, caller.phone, caller.photoUri,
                        schedule.voiceUri, schedule.repeatVoice
                    )
                    CallNotifications.postFallback(context, notification, id)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
