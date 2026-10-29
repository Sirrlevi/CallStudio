package com.callstudio.app.scheduling

import com.callstudio.app.data.CallDao
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.domain.CallEvent
import com.callstudio.app.domain.CallStateMachine
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.domain.RecoveryAction
import com.callstudio.app.domain.RecoveryPlanner
import com.callstudio.app.domain.ScheduleCalculator
import com.callstudio.app.domain.ScheduleResult
import com.callstudio.app.domain.ScheduleRules
import com.callstudio.app.domain.ScheduleSpec
import com.callstudio.app.domain.InvalidReason
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCoordinator(
    private val calls: CallDao,
    private val engine: SchedulingEngine,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zone: () -> ZoneId = { ZoneId.systemDefault() },
) {
    /** Called when a call is due immediately (recovery after reboot, or "start now"). */
    var fireHandler: suspend (Long) -> Unit = {}

    enum class Problem { NOT_POSITIVE, TOO_FAR, IN_THE_PAST, DUPLICATE }

    sealed interface Outcome {
        data class Scheduled(val event: CallEventEntity, val exact: Boolean) : Outcome
        data class Rejected(val problem: Problem) : Outcome
    }

    fun canScheduleExact(): Boolean = engine.canScheduleExact()

    /** Creates a scheduled call, or when [editingEventId] is given and still scheduled, reschedules that one. */
    suspend fun schedule(caller: CallerEntity, spec: ScheduleSpec, editingEventId: Long? = null): Outcome {
        val now = clock()
        val valid = when (val r = ScheduleCalculator.resolve(spec, now, zone())) {
            is ScheduleResult.Invalid -> return Outcome.Rejected(r.reason.toProblem())
            is ScheduleResult.Valid -> r
        }
        if (spec != ScheduleSpec.Immediate) {
            val clash = calls.getScheduled().any {
                it.id != editingEventId &&
                    ScheduleRules.isDuplicate(it.callerId, it.triggerAtMillis, caller.id, valid.triggerAtMillis)
            }
            if (clash) return Outcome.Rejected(Problem.DUPLICATE)
        }
        val existing = editingEventId?.let { calls.get(it) }?.takeIf { it.status == CallStatus.SCHEDULED.name }
        val base = CallEventEntity(
            id = existing?.id ?: 0,
            callerId = caller.id,
            callerName = caller.name,
            callerPhone = caller.phone,
            callerPhotoFile = caller.photoFile,
            triggerAtMillis = valid.triggerAtMillis,
            wallClock = valid.wallClock?.toString(),
            status = CallStatus.SCHEDULED.name,
            createdAt = existing?.createdAt ?: now,
        )
        val id = if (existing != null) {
            calls.update(base)
            existing.id
        } else {
            calls.insert(base)
        }
        if (spec == ScheduleSpec.Immediate) return Outcome.Scheduled(base.copy(id = id), exact = true)
        val exact = engine.schedule(id, valid.triggerAtMillis)
        val saved = base.copy(id = id, exactAlarm = exact)
        calls.update(saved)
        return Outcome.Scheduled(saved, exact)
    }

    /** Cancels a call that has not fired yet. Returns false for anything else. */
    suspend fun cancel(eventId: Long): Boolean {
        val e = calls.get(eventId) ?: return false
        if (e.status != CallStatus.SCHEDULED.name) return false
        if (CallStateMachine.next(CallStatus.SCHEDULED, CallEvent.CANCEL) != CallStatus.CANCELLED) return false
        engine.cancel(eventId)
        calls.update(e.copy(status = CallStatus.CANCELLED.name, endedAtMillis = clock(), endReason = "Cancelled"))
        return true
    }

    suspend fun cancelForCaller(callerId: Long) {
        calls.getScheduled().filter { it.callerId == callerId }.forEach { cancel(it.id) }
    }

    /**
     * Re-arms every pending call. Runs on app start, after reboot / app update, and (with
     * [recomputeWallClock]) after a time-zone or clock change. Calls that are overdue by more than the grace
     * period are recorded as missed rather than ringing late.
     */
    suspend fun recover(recomputeWallClock: Boolean) {
        val now = clock()
        val zoneId = zone()
        for (stored in calls.getScheduled()) {
            var event = stored
            val wall = stored.wallClock
            if (recomputeWallClock && wall != null) {
                val t = ScheduleCalculator.wallClockToMillis(LocalDateTime.parse(wall), zoneId)
                if (t != stored.triggerAtMillis) {
                    event = stored.copy(triggerAtMillis = t)
                    calls.update(event)
                }
            }
            when (val action = RecoveryPlanner.plan(event.triggerAtMillis, now)) {
                is RecoveryAction.Reschedule -> {
                    val exact = engine.schedule(event.id, action.triggerAtMillis)
                    if (exact != event.exactAlarm) calls.update(event.copy(exactAlarm = exact))
                }
                RecoveryAction.FireNow -> {
                    engine.cancel(event.id)
                    fireHandler(event.id)
                }
                RecoveryAction.MarkMissed -> {
                    engine.cancel(event.id)
                    calls.update(
                        event.copy(
                            status = CallStatus.MISSED.name,
                            endedAtMillis = now,
                            endReason = "Missed while the device was off or the app was stopped",
                        )
                    )
                }
            }
        }
    }

    private fun InvalidReason.toProblem(): Problem = when (this) {
        InvalidReason.NOT_POSITIVE -> Problem.NOT_POSITIVE
        InvalidReason.TOO_FAR -> Problem.TOO_FAR
        InvalidReason.IN_THE_PAST -> Problem.IN_THE_PAST
    }
}
