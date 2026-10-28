package com.callstudio.app

import com.callstudio.app.scheduling.CallEvent
import com.callstudio.app.scheduling.CallPhase
import com.callstudio.app.scheduling.CallStateMachine
import com.callstudio.app.scheduling.SchedulerMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SchedulerMathTest {
    @Test fun delayAddsSecondsInMillis() {
        assertEquals(15_000L, SchedulerMath.afterSeconds(10_000L, 5L))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeDelayIsRejected() {
        SchedulerMath.afterSeconds(10_000L, -1L)
    }

    @Test fun triggerMustBeInTheFuture() {
        assertTrue(SchedulerMath.validateTrigger(100L, 101L))
        assertFalse(SchedulerMath.validateTrigger(100L, 100L))
        assertFalse(SchedulerMath.validateTrigger(100L, 99L))
    }

    @Test fun callStateTransitionsRejectInvalidActions() {
        assertEquals(CallPhase.RINGING, CallStateMachine.transition(CallPhase.SCHEDULED, CallEvent.FIRE))
        assertEquals(CallPhase.ACTIVE, CallStateMachine.transition(CallPhase.RINGING, CallEvent.ANSWER))
        assertEquals(CallPhase.ENDED, CallStateMachine.transition(CallPhase.ACTIVE, CallEvent.END))
        assertNull(CallStateMachine.transition(CallPhase.ENDED, CallEvent.ANSWER))
    }
}
