package com.callstudio.app.fakeout

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack

/**
 * Plays the synthesized call tones on the voice-call stream, so they come out of the earpiece (or the speaker
 * when it is on) and follow the call volume keys, like a real call's audio. Everything is released by [stopAll].
 */
class TonePlayer(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private class Active(val track: AudioTrack, val frames: Int)

    private val active = mutableListOf<Active>()
    private var loop: AudioTrack? = null
    private var focus: AudioFocusRequest? = null

    /** Like a real call, other audio (music, videos) pauses while the call is up. */
    @Synchronized
    fun requestFocus() {
        if (focus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener { }
            .build()
        focus = request
        runCatching { audio.requestAudioFocus(request) }
    }

    @Synchronized
    fun abandonFocus() {
        focus?.let { runCatching { audio.abandonAudioFocusRequest(it) } }
        focus = null
    }

    /** Loops [pcm] until [stopLoop]. Used for the ringback tone. */
    @Synchronized
    fun startLoop(pcm: ShortArray) {
        stopLoop()
        val track = build(pcm, loop = true) ?: return
        if (runCatching { track.play() }.isSuccess) loop = track else track.release()
    }

    @Synchronized
    fun stopLoop() {
        loop?.let {
            runCatching { it.setVolume(0f) }
            runCatching { it.stop() }
            it.release()
        }
        loop = null
    }

    /** Plays [pcm] once; returns false when the device couldn't create the track (the call just goes on silently). */
    @Synchronized
    fun play(pcm: ShortArray): Boolean {
        if (pcm.isEmpty()) return false
        prune()
        val track = build(pcm, loop = false) ?: return false
        if (runCatching { track.play() }.isFailure) {
            track.release()
            return false
        }
        active += Active(track, pcm.size)
        return true
    }

    @Synchronized
    fun stopAll() {
        stopLoop()
        active.forEach {
            runCatching { it.track.stop() }
            it.track.release()
        }
        active.clear()
    }

    private fun prune() {
        val done = active.filter { runCatching { it.track.playbackHeadPosition }.getOrDefault(Int.MAX_VALUE) >= it.frames }
        done.forEach { it.track.release() }
        active.removeAll(done.toSet())
    }

    private fun build(pcm: ShortArray, loop: Boolean): AudioTrack? = try {
        val track = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(ToneSynth.SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        if (loop) track.setLoopPoints(0, pcm.size, -1)
        track
    } catch (e: Exception) {
        null
    }
}
