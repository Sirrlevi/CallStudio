package com.callstudio.app.session

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.callstudio.app.CallStudioApp
import com.callstudio.app.diag.CallDiagnostics
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Thin foreground service: keeps the process alive and shows the call notification while a call rings or is
 * active. All real work happens in [CallSessionManager]. It is never sticky; a killed call is not resurrected.
 */
class CallService : Service() {
    private var scope: CoroutineScope? = null

    private data class Key(val status: CallStatus?, val eventId: Long, val muted: Boolean, val recording: Boolean, val name: String)

    private val sessions get() = (application as CallStudioApp).container.sessions
    private val notifications get() = (application as CallStudioApp).container.notifications

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { sc ->
            sc.launch {
                sessions.session
                    .map { Key(it.status, it.eventId, it.muted, it.recording.isActive, it.callerName) }
                    .distinctUntilChanged()
                    .collect { refresh() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val s = sessions.session.value
        if (intent?.action == ACTION_RECORD && s.status == CallStatus.ACTIVE) {
            if (promote(microphone = true)) sessions.beginRecording() else sessions.recordingUnavailable()
        } else {
            val ok = promote(microphone = s.recording.isActive)
            if (!ok && s.status == CallStatus.RINGING) sessions.onServiceFailed()
            if (ok && s.status == CallStatus.RINGING) sessions.beginRinging()
        }
        if (!sessions.session.value.isLive) shutDown()
        return START_NOT_STICKY
    }

    private fun refresh() {
        val s = sessions.session.value
        if (!s.isLive) {
            shutDown()
        } else if (!promote(microphone = s.recording.isActive) && s.status == CallStatus.RINGING) {
            sessions.onServiceFailed()
        }
    }

    /** Shows the right notification and declares the foreground-service types that are currently legal. */
    private fun promote(microphone: Boolean): Boolean {
        val s = sessions.session.value
        val notification = try {
            when (s.status) {
                // Keeps the "backup" (system ringtone) version when the app's own ringtone had to be replaced.
                CallStatus.RINGING -> notifications.buildIncoming(s, backup = sessions.systemRing)
                CallStatus.ACTIVE -> notifications.buildOngoing(s)
                else -> notifications.buildEnded()
            }
        } catch (e: Exception) {
            // Android kills a service that was started as a foreground service and never shows a notification.
            Log.e(TAG, "Could not build the call notification", e)
            CallDiagnostics.log("Call notification could not be built", "${e.javaClass.simpleName}: ${e.message}")
            notifications.buildEnded()
        }
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            if (microphone && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        }
        return try {
            ServiceCompat.startForeground(this, NotificationHelper.NOTIFICATION_ID, notification, type)
            true
        } catch (e: Exception) {
            Log.w(TAG, "startForeground was refused", e)
            CallDiagnostics.log("Ringing service was refused by Android", "${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    private fun shutDown() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope?.cancel()
        scope = null
        isRunning = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallService"
        const val ACTION_SYNC = "com.callstudio.app.action.SYNC"
        const val ACTION_RECORD = "com.callstudio.app.action.RECORD"

        @Volatile
        var isRunning: Boolean = false
            private set

        /** Returns false when Android refuses to start a foreground service right now. */
        fun start(context: Context, action: String = ACTION_SYNC): Boolean = try {
            ContextCompat.startForegroundService(context, Intent(context, CallService::class.java).setAction(action))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Foreground service start refused", e)
            false
        }
    }
}
