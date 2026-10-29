package com.callstudio.app

import com.callstudio.app.domain.InvalidReason
import com.callstudio.app.domain.ScheduleCalculator
import com.callstudio.app.domain.ScheduleResult
import com.callstudio.app.domain.ScheduleSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleCalculatorTest {
    private val utc = ZoneId.of("UTC")
    private val now = 1_700_000_000_000L

    @Test fun immediateIsNow() {
        val r = ScheduleCalculator.resolve(ScheduleSpec.Immediate, now, utc) as ScheduleResult.Valid
        assertEquals(now, r.triggerAtMillis)
        assertNull(r.wallClock)
    }

    @Test fun secondsAndMinutesAddToNow() {
        val s = ScheduleCalculator.resolve(ScheduleSpec.AfterSeconds(30), now, utc) as ScheduleResult.Valid
        val m = ScheduleCalculator.resolve(ScheduleSpec.AfterMinutes(5), now, utc) as ScheduleResult.Valid
        assertEquals(now + 30_000, s.triggerAtMillis)
        assertEquals(now + 300_000, m.triggerAtMillis)
    }

    @Test fun zeroAndNegativeAreRejected() {
        assertEquals(ScheduleResult.Invalid(InvalidReason.NOT_POSITIVE), ScheduleCalculator.resolve(ScheduleSpec.AfterSeconds(0), now, utc))
        assertEquals(ScheduleResult.Invalid(InvalidReason.NOT_POSITIVE), ScheduleCalculator.resolve(ScheduleSpec.AfterMinutes(-3), now, utc))
    }

    @Test fun hugeValuesAreRejectedWithoutOverflow() {
        assertEquals(ScheduleResult.Invalid(InvalidReason.TOO_FAR), ScheduleCalculator.resolve(ScheduleSpec.AfterMinutes(Long.MAX_VALUE), now, utc))
        assertEquals(ScheduleResult.Invalid(InvalidReason.TOO_FAR), ScheduleCalculator.resolve(ScheduleSpec.AfterSeconds(Long.MAX_VALUE / 10), now, utc))
    }

    @Test fun pastLocalTimeIsRejected() {
        val past = LocalDateTime.of(2001, 1, 1, 0, 0)
        assertEquals(ScheduleResult.Invalid(InvalidReason.IN_THE_PAST), ScheduleCalculator.resolve(ScheduleSpec.AtLocal(past), now, utc))
    }

    @Test fun localTimeUsesTheGivenZone() {
        val target = LocalDateTime.of(2024, 6, 1, 12, 0)
        val inUtc = ScheduleCalculator.resolve(ScheduleSpec.AtLocal(target), now, utc) as ScheduleResult.Valid
        val inTokyo = ScheduleCalculator.resolve(ScheduleSpec.AtLocal(target), now, ZoneId.of("Asia/Tokyo")) as ScheduleResult.Valid
        assertEquals(9 * 3_600_000L, inUtc.triggerAtMillis - inTokyo.triggerAtMillis)
        assertEquals(target, inUtc.wallClock)
    }

    @Test fun dstGapTimeIsMovedForwardNotRejected() {
        // 02:30 does not exist on 2030-03-10 in New York; java.time shifts it to 03:30.
        val ny = ZoneId.of("America/New_York")
        val gap = LocalDateTime.of(2030, 3, 10, 2, 30)
        val millis = ScheduleCalculator.wallClockToMillis(gap, ny)
        assertEquals(ScheduleCalculator.wallClockToMillis(LocalDateTime.of(2030, 3, 10, 3, 30), ny), millis)
    }

    @Test fun overOneYearAheadIsRejected() {
        val far = LocalDateTime.of(2090, 1, 1, 0, 0)
        assertTrue(ScheduleCalculator.resolve(ScheduleSpec.AtLocal(far), now, utc) is ScheduleResult.Invalid)
    }
}
