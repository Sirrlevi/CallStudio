package com.callstudio.app.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import com.callstudio.app.audio.AudioFiles
import com.callstudio.app.container
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.domain.ScheduleSpec
import com.callstudio.app.permissions.DoNotDisturb
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.preferences.AppSettings
import com.callstudio.app.scheduling.ScheduleCoordinator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScheduleViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ctx: Application = app

    val callers: StateFlow<List<CallerEntity>?> =
        c.callers.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val scheduled: StateFlow<List<CallEventEntity>?> =
        c.db.callDao().observeScheduled().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> =
        c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val session = c.sessions.session

    suspend fun caller(id: Long): CallerEntity? = c.callers.get(id)
    suspend fun event(id: Long): CallEventEntity? = c.db.callDao().get(id)

    fun selectCaller(id: Long) {
        viewModelScope.launch { c.settings.setLastCaller(id) }
    }

    /** Starts a ringing call right now; returns its id, or null when it couldn't start. */
    suspend fun startNow(callerId: Long, voice: ScheduleCoordinator.VoiceClip? = null): Long? {
        val live = c.sessions.session.value
        if (live.isLive) return live.eventId
        val caller = c.callers.get(callerId) ?: return null
        return when (val outcome = c.coordinator.schedule(caller, ScheduleSpec.Immediate, null, voice)) {
            is ScheduleCoordinator.Outcome.Rejected -> null
            is ScheduleCoordinator.Outcome.Scheduled -> {
                c.settings.setLastCaller(caller.id)
                c.callers.touch(caller.id)
                c.sessions.onAlarm(outcome.event.id)
                if (c.sessions.session.value.status == CallStatus.RINGING) outcome.event.id else null
            }
        }
    }

    suspend fun schedule(
        caller: CallerEntity,
        spec: ScheduleSpec,
        editingEventId: Long?,
        voice: ScheduleCoordinator.VoiceClip? = null,
    ): ScheduleCoordinator.Outcome {
        val outcome = c.coordinator.schedule(caller, spec, editingEventId, voice)
        if (outcome is ScheduleCoordinator.Outcome.Scheduled) {
            c.settings.setLastCaller(caller.id)
            c.callers.touch(caller.id)
        }
        return outcome
    }

    fun cancel(eventId: Long) {
        viewModelScope.launch { c.coordinator.cancel(eventId) }
    }

    /**
     * Keeps access to the chosen audio file and checks that Android can play it. Returns the file's display name,
     * or null when it can't be played (the caller then shows a message).
     */
    suspend fun acceptVoiceFile(uri: Uri): String? {
        AudioFiles.persist(ctx, uri)
        val playable = withContext(Dispatchers.IO) { AudioFiles.isPlayable(ctx, uri) }
        if (!playable) {
            c.voiceLeases.releaseIfUnused(uri.toString())
            return null
        }
        return AudioFiles.displayName(ctx, uri) ?: "Audio file"
    }

    /** Drops access to a file that was picked but never saved with a call. */
    fun discardVoiceFile(uri: String?) {
        viewModelScope.launch { c.voiceLeases.releaseIfUnused(uri) }
    }

    fun exactAllowed(): Boolean = SystemAccess.exactAlarmsAllowed(ctx)

    /** Human-readable list of things that would stop calls from showing up properly. */
    fun setupGaps(): List<String> = buildList {
        if (!SystemAccess.notificationsEnabled(ctx)) add("Notifications are off")
        else if (!c.notifications.incomingChannelAllowsPopUp()) add("Incoming-call alerts are turned down in Android settings")
        if (!SystemAccess.fullScreenAllowed(ctx)) add("Full-screen call screen is not allowed")
        if (!SystemAccess.exactAlarmsAllowed(ctx)) add("Exact timing is not allowed")
        if (DoNotDisturb.mayStaySilent(DoNotDisturb.read(ctx), c.notifications.incomingChannelOverridesDnd())) {
            add("Do Not Disturb is on, so calls may stay silent")
        }
    }
}
