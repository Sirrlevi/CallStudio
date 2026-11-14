package com.callstudio.app.domain

import java.util.Locale

/**
 * The "ongoing call timer" setting: every call's timer starts at this time instead of 0:00 and keeps running, so a call
 * can look as if it has already been going for a while. It applies to incoming and outgoing calls alike.
 */
object CallTimerOffset {
    const val MAX_HOURS = 9_999L
    const val MAX_SECONDS = MAX_HOURS * 3_600L + 59L * 60L + 59L

    fun clamp(seconds: Long): Long = seconds.coerceIn(0L, MAX_SECONDS)

    fun toMillis(seconds: Long): Long = clamp(seconds) * 1_000L

    /** Hours are free (up to [MAX_HOURS]); minutes and seconds stay within 59 so the result reads like a clock. */
    fun fromParts(hours: Long, minutes: Long, seconds: Long): Long =
        clamp(hours.coerceIn(0L, MAX_HOURS) * 3_600L + minutes.coerceIn(0L, 59L) * 60L + seconds.coerceIn(0L, 59L))

    /**
     * The clock base for a timer that must read [seconds] at this very moment and keep running (the timer shows
     * `nowElapsed - base`). A time of 0 means a normal timer, so the base goes back to the call's real start.
     */
    fun liveBase(nowElapsed: Long, realStartElapsed: Long, seconds: Long): Long =
        if (clamp(seconds) == 0L) realStartElapsed else nowElapsed - toMillis(seconds)

    fun hours(seconds: Long): Long = clamp(seconds) / 3_600L

    fun minutes(seconds: Long): Long = clamp(seconds) % 3_600L / 60L

    fun secondsPart(seconds: Long): Long = clamp(seconds) % 60L

    /** "1:27:20", always with hours, so Settings reads the same way the timer will. */
    fun label(seconds: Long): String {
        val c = clamp(seconds)
        return String.format(Locale.ROOT, "%d:%02d:%02d", c / 3_600L, c % 3_600L / 60L, c % 60L)
    }
}
