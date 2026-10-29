package com.callstudio.app

import com.callstudio.app.domain.CallEvent
import com.callstudio.app.domain.CallStateMachine
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.domain.RecoveryAction
import com.callstudio.app.domain.RecoveryPlanner
import com.callstudio.app.domain.ScheduleRules
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.notifications.RequestCodes
import com.callstudio.app.recording.WavFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class StateAndRulesTest {
    @Test fun happyPathIsScheduledRingingActiveEnded() {
        var s = CallStatus.SCHEDULED
        s = CallStateMachine.next(s, CallEvent.FIRE)!!
        assertEquals(CallStatus.RINGING, s)
        s = CallStateMachine.next(s, CallEvent.ANSWER)!!
        assertEquals(CallStatus.ACTIVE, s)
        s = CallStateMachine.next(s, CallEvent.END)!!
        assertEquals(CallStatus.ENDED, s)
    }

    @Test fun declineTimeoutAndCancelAreDistinctOutcomes() {
        assertEquals(CallStatus.REJECTED, CallStateMachine.next(CallStatus.RINGING, CallEvent.DECLINE))
        assertEquals(CallStatus.MISSED, CallStateMachine.next(CallStatus.RINGING, CallEvent.RING_TIMEOUT))
        assertEquals(CallStatus.CANCELLED, CallStateMachine.next(CallStatus.SCHEDULED, CallEvent.CANCEL))
        assertEquals(CallStatus.ERROR, CallStateMachine.next(CallStatus.ACTIVE, CallEvent.FAIL))
    }

    @Test fun ringingCannotEndAndActiveCannotBeAnsweredAgain() {
        assertNull(CallStateMachine.next(CallStatus.RINGING, CallEvent.END))
        assertNull(CallStateMachine.next(CallStatus.ACTIVE, CallEvent.ANSWER))
        assertNull(CallStateMachine.next(CallStatus.ACTIVE, CallEvent.DECLINE))
        assertNull(CallStateMachine.next(CallStatus.SCHEDULED, CallEvent.ANSWER))
    }

    @Test fun terminalStatesAcceptNothing() {
        val terminal = CallStatus.entries.filter { it.isTerminal }
        assertTrue(terminal.containsAll(listOf(CallStatus.ENDED, CallStatus.CANCELLED, CallStatus.REJECTED, CallStatus.MISSED, CallStatus.ERROR)))
        for (t in terminal) for (e in CallEvent.entries) assertNull("$t + $e", CallStateMachine.next(t, e))
    }

    @Test fun recoveryPlansByOverdueAmount() {
        val now = 10_000_000L
        assertEquals(RecoveryAction.Reschedule(now + 5_000), RecoveryPlanner.plan(now + 5_000, now))
        assertEquals(RecoveryAction.FireNow, RecoveryPlanner.plan(now - 60_000, now))
        assertEquals(RecoveryAction.MarkMissed, RecoveryPlanner.plan(now - RecoveryPlanner.GRACE_MILLIS - 1, now))
    }

    @Test fun duplicateRuleMatchesSameCallerWithinWindowOnly() {
        assertTrue(ScheduleRules.isDuplicate(1, 1_000, 1, 10_000))
        assertFalse(ScheduleRules.isDuplicate(1, 1_000, 1, 1_000 + ScheduleRules.DUPLICATE_WINDOW_MILLIS))
        assertFalse(ScheduleRules.isDuplicate(1, 1_000, 2, 1_000))
    }

    @Test fun requestCodesAreUniquePerEventAndPurpose() {
        val purposes = listOf(RequestCodes.ALARM, RequestCodes.OPEN, RequestCodes.ANSWER, RequestCodes.DECLINE, RequestCodes.HANG_UP, RequestCodes.MUTE)
        val codes = (1L..50L).flatMap { id -> purposes.map { RequestCodes.forEvent(id, it) } }
        assertEquals(codes.size, codes.toSet().size)
        assertTrue(codes.all { it >= 0 })
    }

    @Test fun wavHeaderDescribesPcm16Mono() {
        val h = WavFormat.header(dataBytes = 32_000, sampleRate = 16_000)
        assertEquals(WavFormat.HEADER_SIZE, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 32_000, b.getInt(4))
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28))
        assertEquals(32_000, b.getInt(40))
        assertEquals(1_000L, WavFormat.durationMs(32_000, 16_000))
    }

    @Test fun elapsedAndCountdownFormatting() {
        assertEquals("0:00", TimeFormat.elapsed(-5))
        assertEquals("1:05", TimeFormat.elapsed(65_000))
        assertEquals("1:01:01", TimeFormat.elapsed(3_661_000))
        assertEquals("now", TimeFormat.countdown(0))
        assertEquals("in 30 s", TimeFormat.countdown(30_000))
        assertEquals("in 2 min", TimeFormat.countdown(61_000))
        assertEquals("in 1 h 30 min", TimeFormat.countdown(5_400_000))
    }
}
