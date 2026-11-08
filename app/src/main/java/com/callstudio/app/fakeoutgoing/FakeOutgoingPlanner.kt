package com.callstudio.app.fakeoutgoing

import kotlin.math.sqrt
import kotlin.random.Random

enum class FakeOutcome(val label: String) {
    ANSWERED("Answered"),
    NO_ANSWER("No answer"),
    NETWORK_DROP("Network drop"),
    BUSY("Busy"),
    REJECTED("Declined"),
}

/** [connectingMs] is the silent "Calling" stage; [ringMs] is how long it then rings before the outcome. */
data class FakeOutgoingPlan(val outcome: FakeOutcome, val connectingMs: Long, val ringMs: Long) {
    val outcomeAtMs: Long get() = connectingMs + ringMs
}

/**
 * Decides how a fake outgoing call plays out. Whatever the outcome, it rings for at least 20 seconds, so a call is
 * never picked up or cut straight away. Total length stays around 45 seconds at most.
 */
object FakeOutgoingPlanner {
    const val MIN_RING_MS = 20_000L
    const val MAX_RING_MS = 42_000L

    /** Relative likelihood of each outcome when nothing is forced. */
    val weights: Map<FakeOutcome, Int> = linkedMapOf(
        FakeOutcome.ANSWERED to 45,
        FakeOutcome.NO_ANSWER to 20,
        FakeOutcome.REJECTED to 12,
        FakeOutcome.NETWORK_DROP to 12,
        FakeOutcome.BUSY to 11,
    )

    fun plan(random: Random, forced: FakeOutcome? = null): FakeOutgoingPlan {
        val outcome = forced ?: pick(random)
        val connecting = uniform(random, 1_400, 3_900)
        val ring = when (outcome) {
            // Most people pick up after four to six rings, a few much later.
            FakeOutcome.ANSWERED -> triangular(random, 20_300, 40_000, 26_000)
            FakeOutcome.NO_ANSWER -> uniform(random, 36_000, MAX_RING_MS)
            FakeOutcome.REJECTED -> uniform(random, 20_300, 32_000)
            FakeOutcome.BUSY -> uniform(random, 20_300, 38_000)
            FakeOutcome.NETWORK_DROP -> uniform(random, 20_300, MAX_RING_MS)
        }
        return FakeOutgoingPlan(outcome, connecting, ring.coerceIn(MIN_RING_MS, MAX_RING_MS))
    }

    private fun pick(random: Random): FakeOutcome {
        var roll = random.nextInt(weights.values.sum())
        for ((outcome, weight) in weights) {
            if (roll < weight) return outcome
            roll -= weight
        }
        return FakeOutcome.ANSWERED
    }

    private fun uniform(random: Random, min: Long, max: Long): Long = random.nextLong(min, max + 1)

    internal fun triangular(random: Random, min: Long, max: Long, mode: Long): Long {
        val a = min.toDouble()
        val b = max.toDouble()
        val c = mode.toDouble()
        val u = random.nextDouble()
        val split = (c - a) / (b - a)
        val x = if (u < split) a + sqrt(u * (b - a) * (c - a)) else b - sqrt((1 - u) * (b - a) * (b - c))
        return x.toLong().coerceIn(min, max)
    }
}
