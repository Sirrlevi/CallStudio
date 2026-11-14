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
    fun `a live re-base makes the running timer read the saved time right now`() {
        val realStart = 1_000_000L
        val now = 1_045_000L // the call has really run for 45 seconds
        val base = CallTimerOffset.liveBase(now, realStart, CallTimerOffset.fromParts(1, 30, 0))
        assertEquals("1:30:00", TimeFormat.elapsed(now - base))
        assertEquals("1:30:05", TimeFormat.elapsed(now + 5_000L - base))
    }

    @Test
    fun `a live re-base to zero goes back to the real call time`() {
        val realStart = 1_000_000L
        val now = 1_045_000L
        val base = CallTimerOffset.liveBase(now, realStart, 0L)
        assertEquals(realStart, base)
        assertEquals("0:45", TimeFormat.elapsed(now - base))
    }

    @Test
    fun `a live re-base can go back to a smaller time too`() {
        val now = 5_000_000L
        val base = CallTimerOffset.liveBase(now, 4_000_000L, 90L)
        assertEquals("1:30", TimeFormat.elapsed(now - base))
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
