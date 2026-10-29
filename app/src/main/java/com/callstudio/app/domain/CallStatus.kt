package com.callstudio.app.domain

enum class CallStatus {
    SCHEDULED, RINGING, ACTIVE, ENDED, CANCELLED, REJECTED, MISSED, ERROR;

    val isLive: Boolean get() = this == RINGING || this == ACTIVE
    val isTerminal: Boolean get() = !(this == SCHEDULED || isLive)

    companion object {
        fun parse(value: String?): CallStatus = entries.firstOrNull { it.name == value } ?: ERROR
    }
}

enum class CallEvent { FIRE, ANSWER, DECLINE, END, CANCEL, RING_TIMEOUT, FAIL }

/** Pure transition table: SCHEDULED -> RINGING -> ACTIVE -> ENDED, plus cancel / reject / miss / error. */
object CallStateMachine {
    fun next(from: CallStatus, event: CallEvent): CallStatus? = when (from) {
        CallStatus.SCHEDULED -> when (event) {
            CallEvent.FIRE -> CallStatus.RINGING
            CallEvent.CANCEL -> CallStatus.CANCELLED
            CallEvent.FAIL -> CallStatus.ERROR
            else -> null
        }
        CallStatus.RINGING -> when (event) {
            CallEvent.ANSWER -> CallStatus.ACTIVE
            CallEvent.DECLINE -> CallStatus.REJECTED
            CallEvent.RING_TIMEOUT -> CallStatus.MISSED
            CallEvent.CANCEL -> CallStatus.CANCELLED
            CallEvent.FAIL -> CallStatus.ERROR
            else -> null
        }
        CallStatus.ACTIVE -> when (event) {
            CallEvent.END -> CallStatus.ENDED
            CallEvent.FAIL -> CallStatus.ERROR
            else -> null
        }
        else -> null
    }
}
