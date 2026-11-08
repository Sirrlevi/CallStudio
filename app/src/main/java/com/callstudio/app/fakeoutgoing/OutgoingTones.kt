package com.callstudio.app.fakeoutgoing

import android.media.AudioManager
import android.media.ToneGenerator

/**
 * The phone's own supervisory tones (the ones the system dialer uses), played on the call stream, so ringback, busy
 * and failure sounds match the device and region instead of being imitations.
 */
class OutgoingTones {
    private var generator: ToneGenerator? = null

    private fun tone(): ToneGenerator? {
        if (generator == null) {
            generator = try {
                ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100)
            } catch (e: RuntimeException) {
                null
            }
        }
        return generator
    }

    private fun play(type: Int, durationMs: Int = -1) {
        val g = tone() ?: return
        g.stopTone()
        if (durationMs > 0) g.startTone(type, durationMs) else g.startTone(type)
    }

    /** A soft acknowledgement as the call is placed. */
    fun connecting() = play(ToneGenerator.TONE_PROP_ACK, 120)

    /** The ringing the caller hears; continues until [stop]. */
    fun ringback() = play(ToneGenerator.TONE_SUP_RINGTONE)

    /** Busy signal; continues until [stop]. */
    fun busy() = play(ToneGenerator.TONE_SUP_BUSY)

    /** The tone a phone gives when the network lets go of a call. */
    fun networkFailure() = play(ToneGenerator.TONE_SUP_RADIO_NOTAVAIL)

    /** Short prompt when a call finishes. */
    fun callEnded() = play(ToneGenerator.TONE_PROP_PROMPT, 400)

    /** Tiny click the moment the other side picks up. */
    fun connected() = play(ToneGenerator.TONE_PROP_ACK, 90)

    fun stop() {
        generator?.stopTone()
    }

    fun release() {
        generator?.release()
        generator = null
    }
}
