package com.callstudio.app.domain

import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

sealed interface ScheduleSpec {
    data object Immediate : ScheduleSpec
    data class AfterSeconds(val seconds: Long) : ScheduleSpec
    data class AfterMinutes(val minutes: Long) : ScheduleSpec
    data class AtLocal(val dateTime: LocalDateTime) : ScheduleSpec
}

enum class InvalidReason { NOT_POSITIVE, TOO_FAR, IN_THE_PAST }

sealed interface ScheduleResult {
    /** [wallClock] is set only for "at a local time" schedules so they can follow time-zone changes. */
    data class Valid(val triggerAtMillis: Long, val wallClock: LocalDateTime?) : ScheduleResult
    data class Invalid(val reason: InvalidReason) : ScheduleResult
}

object ScheduleCalculator {
    const val MAX_DELAY_MILLIS: Long = 365L * 24 * 60 * 60 * 1000

    fun resolve(spec: ScheduleSpec, nowMillis: Long, zone: ZoneId): ScheduleResult = when (spec) {
        ScheduleSpec.Immediate -> ScheduleResult.Valid(nowMillis, null)
        is ScheduleSpec.AfterSeconds -> relative(spec.seconds, 1_000L, nowMillis)
        is ScheduleSpec.AfterMinutes -> relative(spec.minutes, 60_000L, nowMillis)
        is ScheduleSpec.AtLocal -> {
            val t = wallClockToMillis(spec.dateTime, zone)
            when {
                t <= nowMillis -> ScheduleResult.Invalid(InvalidReason.IN_THE_PAST)
                t - nowMillis > MAX_DELAY_MILLIS -> ScheduleResult.Invalid(InvalidReason.TOO_FAR)
                else -> ScheduleResult.Valid(t, spec.dateTime)
            }
        }
    }

    private fun relative(amount: Long, unitMillis: Long, now: Long): ScheduleResult {
        if (amount <= 0L) return ScheduleResult.Invalid(InvalidReason.NOT_POSITIVE)
        if (amount > MAX_DELAY_MILLIS / unitMillis) return ScheduleResult.Invalid(InvalidReason.TOO_FAR)
        return ScheduleResult.Valid(now + amount * unitMillis, null)
    }

    /** Times that fall in a DST gap are moved forward by the gap length (java.time default). */
    fun wallClockToMillis(dateTime: LocalDateTime, zone: ZoneId): Long =
        dateTime.atZone(zone).toInstant().toEpochMilli()
}

object ScheduleRules {
    const val DUPLICATE_WINDOW_MILLIS = 30_000L

    fun isDuplicate(existingCallerId: Long?, existingTrigger: Long, callerId: Long?, trigger: Long): Boolean =
        existingCallerId == callerId && abs(existingTrigger - trigger) < DUPLICATE_WINDOW_MILLIS
}

sealed interface RecoveryAction {
    data class Reschedule(val triggerAtMillis: Long) : RecoveryAction
    data object FireNow : RecoveryAction
    data object MarkMissed : RecoveryAction
}

/** Decides what to do with a still-SCHEDULED call after a reboot, app update or app launch. */
object RecoveryPlanner {
    const val GRACE_MILLIS = 2 * 60 * 1000L

    fun plan(triggerAtMillis: Long, nowMillis: Long): RecoveryAction = when {
        triggerAtMillis > nowMillis -> RecoveryAction.Reschedule(triggerAtMillis)
        nowMillis - triggerAtMillis <= GRACE_MILLIS -> RecoveryAction.FireNow
        else -> RecoveryAction.MarkMissed
    }
}
