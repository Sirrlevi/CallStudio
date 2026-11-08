package com.callstudio.app

import com.callstudio.app.fakeoutgoing.FakeOutcome
import com.callstudio.app.fakeoutgoing.FakeOutgoingPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FakeOutgoingPlannerTest {
    private val runs = 20_000

    @Test fun everyOutcomeRingsForAtLeastTwentySeconds() {
        repeat(runs) { seed ->
            val plan = FakeOutgoingPlanner.plan(Random(seed))
            assertTrue("seed $seed ${plan.outcome}", plan.ringMs >= 20_000)
            assertTrue(plan.connectingMs in 1_400..3_900)
        }
    }

    @Test fun aPickUpNeverHappensInstantly() {
        repeat(runs) { seed ->
            val plan = FakeOutgoingPlanner.plan(Random(seed), FakeOutcome.ANSWERED)
            assertTrue(plan.outcomeAtMs >= 21_000)
        }
    }

    @Test fun totalLengthStaysWithinAboutFortyFiveSeconds() {
        repeat(runs) { seed ->
            assertTrue(FakeOutgoingPlanner.plan(Random(seed)).outcomeAtMs <= 46_000)
        }
    }

    @Test fun forcedOutcomeIsAlwaysRespected() {
        for (outcome in FakeOutcome.entries) {
            repeat(500) { seed -> assertEquals(outcome, FakeOutgoingPlanner.plan(Random(seed), outcome).outcome) }
        }
    }

    @Test fun randomPicksUseAllOutcomesWithPickUpsMostCommon() {
        val counts = FakeOutcome.entries.associateWith { 0 }.toMutableMap()
        repeat(runs) { seed ->
            val outcome = FakeOutgoingPlanner.plan(Random(seed)).outcome
            counts[outcome] = counts.getValue(outcome) + 1
        }
        assertTrue(counts.values.all { it > 0 })
        assertEquals(FakeOutcome.ANSWERED, counts.maxByOrNull { it.value }!!.key)
        val answeredShare = counts.getValue(FakeOutcome.ANSWERED).toDouble() / runs
        assertTrue("answered share $answeredShare", answeredShare in 0.40..0.50)
    }

    @Test fun unansweredCallsRingTheLongest() {
        repeat(2_000) { seed ->
            assertTrue(FakeOutgoingPlanner.plan(Random(seed), FakeOutcome.NO_ANSWER).ringMs >= 36_000)
        }
    }

    @Test fun answerTimesVaryInsteadOfRepeating() {
        val distinct = (0 until 200).map { FakeOutgoingPlanner.plan(Random(it), FakeOutcome.ANSWERED).ringMs }.toSet()
        assertTrue(distinct.size > 100)
    }
}
