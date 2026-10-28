package com.callstudio.app.scheduling

object SchedulerMath {
    fun afterSeconds(nowMillis: Long, seconds: Long): Long {
        require(seconds >= 0) { "Delay must not be negative" }
        require(seconds <= Long.MAX_VALUE / 1_000L) { "Delay is too large" }
        return nowMillis + seconds * 1_000L
    }

    fun validateTrigger(nowMillis: Long, triggerAtMillis: Long): Boolean = triggerAtMillis > nowMillis
}

enum class CallPhase { SCHEDULED, RINGING, ACTIVE, ENDED, DECLINED, CANCELLED, FAILED }
enum class CallEvent { FIRE, ANSWER, END, DECLINE, CANCEL, ERROR }

object CallStateMachine {
    fun transition(phase: CallPhase, event: CallEvent): CallPhase? = when (phase to event) {
        CallPhase.SCHEDULED to CallEvent.FIRE -> CallPhase.RINGING
        CallPhase.SCHEDULED to CallEvent.CANCEL -> CallPhase.CANCELLED
        CallPhase.SCHEDULED to CallEvent.ERROR -> CallPhase.FAILED
        CallPhase.RINGING to CallEvent.ANSWER -> CallPhase.ACTIVE
        CallPhase.RINGING to CallEvent.DECLINE -> CallPhase.DECLINED
        CallPhase.RINGING to CallEvent.END -> CallPhase.ENDED
        CallPhase.RINGING to CallEvent.ERROR -> CallPhase.FAILED
        CallPhase.ACTIVE to CallEvent.END -> CallPhase.ENDED
        CallPhase.ACTIVE to CallEvent.ERROR -> CallPhase.FAILED
        else -> null
    }
}
