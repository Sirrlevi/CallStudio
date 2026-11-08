package com.callstudio.app.fakeout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeCallPlannerTest {
    @Test
    fun everyOutcomeRingsAtLeastTwentySecondsByDefault() {
        val random = Random(7)
        repeat(5_000) {
            val p = FakeCallPlanner.plan(random)
            assertTrue("${p.outcome} rang ${p.ringMs}", p.ringMs in 20_000L..45_000L)
        }
    }

    @Test
    fun forcedOutcomeIsHonouredAndStillRingsTwentySeconds() {
        val random = Random(3)
        for (o in FakeOutcome.entries) repeat(500) {
            val p = FakeCallPlanner.plan(random, forced = o)
            assertEquals(o, p.outcome)
            assertTrue(p.ringMs >= 20_000L)
        }
    }

    @Test
    fun earlyBusyAndDropMayBeShorterWhenStrictIsOff() {
        val random = Random(5)
        val limits = FakeCallLimits(strictMinimum = false)
        repeat(500) {
            assertTrue(FakeCallPlanner.plan(random, limits, forced = FakeOutcome.BUSY).ringMs < 20_000L)
            // Answered and rejected calls still never end early.
            assertTrue(FakeCallPlanner.plan(random, limits, forced = FakeOutcome.ANSWERED).ringMs >= 20_000L)
            assertTrue(FakeCallPlanner.plan(random, limits, forced = FakeOutcome.REJECTED).ringMs >= 20_000L)
        }
    }

    @Test
    fun allFiveOutcomesHappen() {
        val random = Random(11)
        val seen = (1..2_000).map { FakeCallPlanner.plan(random).outcome }.toSet()
        assertEquals(FakeOutcome.entries.toSet(), seen)
    }

    @Test
    fun connectingPhaseIsNeverInstant() {
        val random = Random(1)
        repeat(1_000) { assertTrue(FakeCallPlanner.plan(random).connectMs >= 2_000L) }
    }

    @Test
    fun tonesHaveTheRealCadenceLengths() {
        assertEquals(3_000L, ToneSynth.durationMs(ToneSynth.render(ToneSynth.ringback(ToneRegion.SOUTH_ASIA))))
        assertEquals(1_500L, ToneSynth.durationMs(ToneSynth.render(ToneSynth.busy(ToneRegion.SOUTH_ASIA))))
        assertEquals(6_000L, ToneSynth.durationMs(ToneSynth.render(ToneSynth.ringback(ToneRegion.NANP))))
        assertEquals(ToneRegion.SOUTH_ASIA, ToneSynth.regionFor("in"))
    }

    @Test
    fun dialSequenceSkipsNonKeysAndLimitsLength() {
        assertEquals(10L * 155L, ToneSynth.dialSequenceMs("+91 80590 56588 123"))
        assertEquals(0, ToneSynth.dialSequence("abc").size)
    }
}
