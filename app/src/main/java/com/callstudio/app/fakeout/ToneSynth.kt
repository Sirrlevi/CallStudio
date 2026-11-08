package com.callstudio.app.fakeout

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/** Which national tone plan the called number's network would use. */
enum class ToneRegion { SOUTH_ASIA, UK, NANP, EUROPE }

/**
 * One beat of a call-progress tone: [freqs] mixed together for [onMs], then [offMs] of silence.
 * [amHz] > 0 adds the slow amplitude wobble that real ringback tones have.
 */
data class TonePart(
    val freqs: List<Double>,
    val onMs: Int,
    val offMs: Int,
    val amp: Double = 0.34,
    val amHz: Double = 0.0,
    val amDepth: Double = 0.0,
)

/**
 * Builds every sound of the fake call as 16-bit mono PCM, from the real cadences in ITU-T E.180 style national
 * tone plans. There are no audio files in the app: nothing to download, license or lose.
 */
object ToneSynth {
    const val SAMPLE_RATE = 44_100
    private const val FADE_MS = 8

    fun regionFor(iso: String?): ToneRegion = when (iso?.uppercase()) {
        "IN", "PK", "BD", "NP", "LK", "BT", "MV" -> ToneRegion.SOUTH_ASIA
        "GB", "IE", "AU", "NZ", "HK", "SG", "MY", "ZA" -> ToneRegion.UK
        "US", "CA", "PR", "MX", "DO", "JM", "BS", "BB", "TT" -> ToneRegion.NANP
        else -> ToneRegion.EUROPE
    }

    /** The "tring… tring…" the caller hears while the other phone rings. */
    fun ringback(r: ToneRegion): List<TonePart> = when (r) {
        ToneRegion.SOUTH_ASIA -> listOf(
            TonePart(listOf(400.0), 400, 200, amHz = 25.0, amDepth = 0.45),
            TonePart(listOf(400.0), 400, 2000, amHz = 25.0, amDepth = 0.45),
        )
        ToneRegion.UK -> listOf(
            TonePart(listOf(400.0, 450.0), 400, 200),
            TonePart(listOf(400.0, 450.0), 400, 2000),
        )
        ToneRegion.NANP -> listOf(TonePart(listOf(440.0, 480.0), 2000, 4000))
        ToneRegion.EUROPE -> listOf(TonePart(listOf(425.0), 1000, 4000))
    }

    /** Engaged tone. */
    fun busy(r: ToneRegion): List<TonePart> = when (r) {
        ToneRegion.SOUTH_ASIA -> listOf(TonePart(listOf(400.0), 750, 750))
        ToneRegion.UK -> listOf(TonePart(listOf(400.0), 375, 375))
        ToneRegion.NANP -> listOf(TonePart(listOf(480.0, 620.0), 500, 500))
        ToneRegion.EUROPE -> listOf(TonePart(listOf(425.0), 500, 500))
    }

    /** Fast "tu-tu-tu-tu" of a failed or dropped call (congestion / reorder). */
    fun reorder(r: ToneRegion): List<TonePart> = when (r) {
        ToneRegion.SOUTH_ASIA -> listOf(TonePart(listOf(400.0), 250, 250))
        ToneRegion.UK -> listOf(TonePart(listOf(400.0), 400, 350), TonePart(listOf(400.0), 225, 525))
        ToneRegion.NANP -> listOf(TonePart(listOf(480.0, 620.0), 250, 250))
        ToneRegion.EUROPE -> listOf(TonePart(listOf(425.0), 200, 200))
    }

    /** [parts] repeated [times] times as one buffer. */
    fun cycles(parts: List<TonePart>, times: Int): ShortArray {
        val one = render(parts)
        val out = ShortArray(one.size * times)
        for (i in 0 until times) System.arraycopy(one, 0, out, i * one.size, one.size)
        return out
    }

    /** One cadence cycle; looped by the player for ringback. */
    fun render(parts: List<TonePart>): ShortArray {
        val sr = SAMPLE_RATE
        val total = parts.sumOf { frames(it.onMs + it.offMs) }
        val out = ShortArray(total)
        var pos = 0
        for (p in parts) {
            val on = frames(p.onMs)
            val fade = min(on / 2, frames(FADE_MS))
            for (i in 0 until on) {
                var v = 0.0
                for (f in p.freqs) v += sin(2.0 * PI * f * i / sr)
                v /= p.freqs.size
                if (p.amHz > 0.0) v *= 1.0 - p.amDepth + p.amDepth * 0.5 * (1.0 + sin(2.0 * PI * p.amHz * i / sr))
                val env = when {
                    i < fade -> 0.5 - 0.5 * cos(PI * i / fade)
                    i >= on - fade -> 0.5 - 0.5 * cos(PI * (on - 1 - i).coerceAtLeast(0) / fade)
                    else -> 1.0
                }
                out[pos + i] = (v * env * p.amp * Short.MAX_VALUE).toInt().toShort()
            }
            pos += on + frames(p.offMs)
        }
        return out
    }

    private val dtmfRows = doubleArrayOf(697.0, 770.0, 852.0, 941.0)
    private val dtmfCols = doubleArrayOf(1209.0, 1336.0, 1477.0)
    private const val DTMF_KEYS = "123456789*0#"

    /**
     * The key-by-key dial tones of a number being dialed. Digits that aren't keys (spaces, +) are skipped; very
     * long numbers keep only their last [maxDigits] digits so the sequence fits inside the "Calling…" phase.
     */
    fun dialSequence(number: String, maxDigits: Int = 10, toneMs: Int = 85, gapMs: Int = 70): ShortArray {
        val keys = number.filter { it in DTMF_KEYS }.takeLast(maxDigits)
        val parts = keys.map { k ->
            val idx = DTMF_KEYS.indexOf(k)
            TonePart(listOf(dtmfRows[idx / 3], dtmfCols[idx % 3]), toneMs, gapMs, amp = 0.36)
        }
        return if (parts.isEmpty()) ShortArray(0) else render(parts)
    }

    fun dialSequenceMs(number: String, maxDigits: Int = 10, toneMs: Int = 85, gapMs: Int = 70): Long =
        number.filter { it in DTMF_KEYS }.takeLast(maxDigits).length.toLong() * (toneMs + gapMs)

    /** A soft, quickly fading "ping" used as the call-connected cue. */
    fun connectPing(): ShortArray {
        val sr = SAMPLE_RATE
        val n = frames(260)
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val attack = min(1.0, i / frames(4).toDouble())
            val v = (sin(2.0 * PI * 1318.5 * t) * 0.7 + sin(2.0 * PI * 1975.5 * t) * 0.3) * exp(-t / 0.055) * attack
            out[i] = (v * 0.30 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** A burst of low, crackly noise: the line breaking up just before a dropped call. Deterministic. */
    fun lineCrackle(ms: Int = 160): ShortArray {
        val n = frames(ms)
        val out = ShortArray(n)
        var seed = 0x2F6E2B1L
        for (i in 0 until n) {
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            val noise = ((seed ushr 33).toInt() / 1073741824.0 - 1.0)
            val env = sin(PI * i / n)
            // Gate it so it crackles instead of hissing.
            val gate = if ((i / 90) % 3 == 1) 0.0 else 1.0
            out[i] = (noise * env * gate * 0.16 * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    fun durationMs(pcm: ShortArray): Long = pcm.size * 1000L / SAMPLE_RATE

    private fun frames(ms: Int): Int = (ms.toLong() * SAMPLE_RATE / 1000L).toInt()
}
