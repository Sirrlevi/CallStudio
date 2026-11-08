package com.callstudio.app.fakeout

import kotlin.math.pow
import kotlin.random.Random

/** How a fake outgoing call ends. Chosen once, up front, by [FakeCallPlanner]. */
enum class FakeOutcome(val label: String) {
    ANSWERED("Answered"),
    NO_ANSWER("No answer"),
    NETWORK_DROP("Network drop"),
    BUSY("Busy"),
    REJECTED("Rejected"),
}

/**
 * The whole timeline of one fake call, decided before it starts.
 *
 * - [connectMs]: the "Calling…" phase (dialing / connecting to the network).
 * - [ringMs]: how long the "Ringing…" phase lasts before [outcome] happens.
 * - [voiceDelayMs]: after an answer, the pause before the attached audio starts (the "Hello?" gap).
 */
data class FakeCallPlan(
    val outcome: FakeOutcome,
    val connectMs: Long,
    val ringMs: Long,
    val voiceDelayMs: Long,
    /** Whether an operator announcement ("the person is busy…") may play for this outcome. */
    val announce: Boolean,
)

/** Tunable limits. The defaults are the product rules: never answered instantly, rings 20 to 45 seconds. */
data class FakeCallLimits(
    val minRingMs: Long = 20_000L,
    val maxRingMs: Long = 45_000L,
    /** true: busy and network drop also respect [minRingMs]. false: they may come early, like a real network. */
    val strictMinimum: Boolean = true,
)

/**
 * Pure and deterministic for a given [Random], so it is unit tested. Nothing here touches Android.
 */
object FakeCallPlanner {
    /** Natural-looking odds. They only need to be relative. */
    private val weights = mapOf(
        FakeOutcome.ANSWERED to 46.0,
        FakeOutcome.NO_ANSWER to 20.0,
        FakeOutcome.REJECTED to 12.0,
        FakeOutcome.BUSY to 12.0,
        FakeOutcome.NETWORK_DROP to 10.0,
    )

    /**
     * @param previous the outcome of the last fake call, if any. The same outcome twice in a row is made less
     * likely (never impossible), so a run of calls doesn't feel like a loop.
     * @param forced a fixed outcome for testing; the timing is still randomised.
     */
    fun plan(
        random: Random = Random.Default,
        limits: FakeCallLimits = FakeCallLimits(),
        previous: FakeOutcome? = null,
        forced: FakeOutcome? = null,
    ): FakeCallPlan {
        val outcome = forced ?: pickOutcome(random, previous)
        val min = limits.minRingMs
        val max = maxOf(limits.maxRingMs, min + 1_000L)

        val ring = when (outcome) {
            // Most people answer in the 20 to 30 s window; a few leave it until the end.
            FakeOutcome.ANSWERED -> skewed(random, min, max, bias = 1.6)
            // Rings out: close to the operator timeout.
            FakeOutcome.NO_ANSWER -> between(random, maxOf(min, max - 9_000L), max)
            // Someone looks at the phone, then cuts it.
            FakeOutcome.REJECTED -> skewed(random, min, minOf(max, min + 14_000L), bias = 1.3)
            FakeOutcome.BUSY ->
                if (limits.strictMinimum) skewed(random, min, minOf(max, min + 8_000L), bias = 1.2)
                else between(random, 2_500L, 6_500L)
            FakeOutcome.NETWORK_DROP ->
                if (limits.strictMinimum) between(random, min, minOf(max, min + 18_000L))
                else between(random, 4_000L, 32_000L)
        }
        return FakeCallPlan(
            outcome = outcome,
            connectMs = between(random, 2_200L, 4_300L),
            ringMs = ring,
            voiceDelayMs = between(random, 650L, 1_400L),
            announce = random.nextDouble() < ANNOUNCE_CHANCE,
        )
    }

    fun pickOutcome(random: Random, previous: FakeOutcome?): FakeOutcome {
        val adjusted = weights.mapValues { (o, w) -> if (o == previous) w * 0.4 else w }
        var roll = random.nextDouble() * adjusted.values.sum()
        for ((o, w) in adjusted) {
            roll -= w
            if (roll < 0) return o
        }
        return FakeOutcome.ANSWERED
    }

    private fun between(random: Random, from: Long, to: Long): Long =
        if (to <= from) from else from + (random.nextDouble() * (to - from)).toLong()

    /** u^bias pushes values toward [from]; bias 1 is uniform. */
    private fun skewed(random: Random, from: Long, to: Long, bias: Double): Long =
        if (to <= from) from else from + (random.nextDouble().pow(bias) * (to - from)).toLong()

    const val ANNOUNCE_CHANCE = 0.4
}
