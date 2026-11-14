package com.callstudio.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CallTimerOffsetTest {
    @Test
    fun `zero means a normal timer`() {
        assertEquals(0L, CallTimerOffset.toMillis(0L))
        assertEquals("0:00:00", CallTimerOffset.label(0L))
    }

    @Test
    fun `parts add up like a clock`() {
        assertEquals(5_240L, CallTimerOffset.fromParts(1, 27, 20))
        assertEquals(3_600L, CallTimerOffset.fromParts(1, 0, 0))
        assertEquals(59L, CallTimerOffset.fromParts(0, 0, 59))
    }

    @Test
    fun `minutes and seconds never go past 59 and nothing is negative`() {
        assertEquals(CallTimerOffset.fromParts(0, 59, 59), CallTimerOffset.fromParts(0, 75, 99))
        assertEquals(0L, CallTimerOffset.fromParts(-3, -1, -1))
    }

    @Test
    fun `any number of hours is allowed up to the limit`() {
        assertEquals(500L * 3_600L, CallTimerOffset.fromParts(500, 0, 0))
        assertEquals(CallTimerOffset.MAX_SECONDS, CallTimerOffset.fromParts(Long.MAX_VALUE, 59, 59))
        assertEquals(CallTimerOffset.MAX_SECONDS, CallTimerOffset.clamp(Long.MAX_VALUE))
        assertEquals(0L, CallTimerOffset.clamp(-5L))
        assertEquals("9999:59:59", CallTimerOffset.label(CallTimerOffset.MAX_SECONDS))
    }

    @Test
    fun `the three parts round trip`() {
        val seconds = CallTimerOffset.fromParts(12, 34, 56)
        assertEquals(12L, CallTimerOffset.hours(seconds))
        assertEquals(34L, CallTimerOffset.minutes(seconds))
        assertEquals(56L, CallTimerOffset.secondsPart(seconds))
        assertEquals("12:34:56", CallTimerOffset.label(seconds))
    }

    @Test
    fun `milliseconds are seconds times a thousand`() {
        assertEquals(87_000L, CallTimerOffset.toMillis(87L))
        assertEquals(CallTimerOffset.MAX_SECONDS * 1_000L, CallTimerOffset.toMillis(Long.MAX_VALUE))
    }

    @Test
    fun `a call that starts at the offset reads offset plus real time`() {
        val head = CallTimerOffset.toMillis(CallTimerOffset.fromParts(1, 27, 20))
        // The timer shows (now - answeredElapsed); answeredElapsed is shifted back by the offset.
        val answeredElapsed = 1_000_000L - head
        assertEquals("1:27:25", TimeFormat.elapsed(1_005_000L - answeredElapsed))
        assertEquals("1:27:20", TimeFormat.elapsed(1_000_000L - answeredElapsed))
    }
}
