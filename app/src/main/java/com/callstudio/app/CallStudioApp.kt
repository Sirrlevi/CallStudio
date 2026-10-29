package com.callstudio.app

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.room.Room
import com.callstudio.app.data.AppDatabase
import com.callstudio.app.data.CallerRepository
import com.callstudio.app.data.PhotoStore
import com.callstudio.app.data.RecordingRepository
import com.callstudio.app.notifications.NotificationHelper
import com.callstudio.app.preferences.SettingsStore
import com.callstudio.app.scheduling.AndroidAlarmBackend
import com.callstudio.app.scheduling.ScheduleCoordinator
import com.callstudio.app.scheduling.SchedulingEngine
import com.callstudio.app.session.CallSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CallStudioApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.start()
    }
}

class AppContainer(app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val db: AppDatabase = Room.databaseBuilder(app, AppDatabase::class.java, "callstudio.db").build()
    val settings = SettingsStore(app)
    val photos = PhotoStore(app)
    val notifications = NotificationHelper(app, photos)
    val coordinator = ScheduleCoordinator(db.callDao(), SchedulingEngine(AndroidAlarmBackend(app)))
    val callers = CallerRepository(db.callerDao(), photos, coordinator)
    val recordings = RecordingRepository(app, db.recordingDao())
    val sessions = CallSessionManager(app, appScope, db.callDao(), recordings, settings, notifications)

    fun start() {
        notifications.ensureChannels()
        coordinator.fireHandler = { id -> sessions.onAlarm(id) }
        appScope.launch {
            try {
                sessions.reconcileStale()
                recordings.recoverOrphans()
            } catch (t: Throwable) {
                Log.e("CallStudio", "Startup cleanup failed", t)
            } finally {
                sessions.markReady()
            }
            try {
                // Re-arms alarms Android may have dropped (for example after a force-stop).
                coordinator.recover(recomputeWallClock = false)
            } catch (t: Throwable) {
                Log.e("CallStudio", "Couldn't re-arm scheduled calls", t)
            }
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as CallStudioApp).container
