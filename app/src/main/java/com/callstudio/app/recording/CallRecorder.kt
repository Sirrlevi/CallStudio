package com.callstudio.app.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile

sealed interface RecorderState {
    data object Idle : RecorderState
    data class Recording(val elapsedMs: Long) : RecorderState
    data class Paused(val elapsedMs: Long) : RecorderState
    data class Error(val message: String) : RecorderState

    val isActive: Boolean get() = this is Recording || this is Paused
}

data class RecordingOutcome(val file: File, val durationMs: Long, val sampleRate: Int)

/**
 * Records the microphone only, as 16-bit mono WAV. While muted, the microphone is released and silence is
 * written so the timeline stays intact. Pausing stops capture and drops the gap. This does not capture any
 * other app's audio or a phone line; it is a microphone recording.
 */
class CallRecorder(private val context: Context) {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    @Volatile private var muted = false
    @Volatile private var paused = false
    @Volatile private var running = false
    @Volatile private var bytes = 0L
    @Volatile private var failure: String? = null
    private var sampleRate = 16_000
    private var thread: Thread? = null
    private var file: File? = null

    /** @return null on success, otherwise a user-readable reason. */
    @Synchronized
    fun start(sampleRate: Int, startMuted: Boolean): String? {
        if (running) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            return "Microphone permission is needed to record."
        }
        val minBuffer = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return "This device can't record at the selected quality."
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val target = File(dir, "rec_${System.currentTimeMillis()}_$sampleRate.wav")
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT, minBuffer * 2,
            )
        } catch (e: Exception) {
            return "The microphone couldn't be opened."
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return "The microphone is busy or unavailable."
        }
        val out = try {
            BufferedOutputStream(FileOutputStream(target), 32 * 1024)
        } catch (e: IOException) {
            record.release()
            return "Couldn't create the recording file. Check your free storage."
        }
        this.sampleRate = sampleRate
        this.file = target
        bytes = 0L
        failure = null
        muted = startMuted
        paused = false
        running = true
        _state.value = RecorderState.Recording(0L)
        thread = Thread({ loop(record, out, minBuffer) }, "CallStudio-recorder").also { it.start() }
        return null
    }

    fun setMuted(value: Boolean) { muted = value }

    fun pause() {
        if (running && !paused) {
            paused = true
            _state.value = RecorderState.Paused(elapsed())
        }
    }

    fun resume() {
        if (running && paused) {
            paused = false
            _state.value = RecorderState.Recording(elapsed())
        }
    }

    /** Blocking (joins the capture thread); call from a background dispatcher. */
    @Synchronized
    fun stop(): RecordingOutcome? {
        val target = file ?: return null
        running = false
        thread?.join(3000)
        thread = null
        file = null
        val dataBytes = bytes
        runCatching {
            RandomAccessFile(target, "rw").use { raf ->
                raf.seek(0)
                raf.write(WavFormat.header(dataBytes, sampleRate))
            }
        }
        _state.value = RecorderState.Idle
        return RecordingOutcome(target, WavFormat.durationMs(dataBytes, sampleRate), sampleRate)
    }

    private fun elapsed(): Long = WavFormat.durationMs(bytes, sampleRate)

    private fun loop(record: AudioRecord, out: OutputStream, bufferSize: Int) {
        val buf = ByteArray(bufferSize)
        val silence = ByteArray(bufferSize)
        var capturing = false
        var silenceClock = 0L
        var lastPublished = -1L
        try {
            out.write(ByteArray(WavFormat.HEADER_SIZE))
            while (running) {
                if (paused) {
                    if (capturing) { record.stop(); capturing = false }
                    silenceClock = 0L
                    Thread.sleep(40)
                    continue
                }
                if (muted) {
                    if (capturing) { record.stop(); capturing = false }
                    val now = System.nanoTime()
                    if (silenceClock == 0L) silenceClock = now
                    val samples = (now - silenceClock) * sampleRate / 1_000_000_000L
                    var remaining = (samples * 2).toInt()
                    if (remaining >= 2) {
                        silenceClock += samples * 1_000_000_000L / sampleRate
                        while (remaining > 0) {
                            val n = minOf(remaining, silence.size)
                            out.write(silence, 0, n)
                            bytes += n
                            remaining -= n
                        }
                    }
                    Thread.sleep(20)
                } else {
                    silenceClock = 0L
                    if (!capturing) { record.startRecording(); capturing = true }
                    val n = record.read(buf, 0, buf.size)
                    if (n > 0) {
                        out.write(buf, 0, n)
                        bytes += n
                    } else if (n < 0) {
                        failure = "Recording was interrupted by the system."
                        break
                    }
                }
                val ms = elapsed()
                if (!paused && ms / 200 != lastPublished) {
                    lastPublished = ms / 200
                    _state.value = RecorderState.Recording(ms)
                }
            }
        } catch (e: IOException) {
            failure = "Couldn't write the recording. Check your free storage."
        } catch (e: InterruptedException) {
            // stop() joined us; fall through to cleanup.
        } catch (e: Exception) {
            failure = "Recording stopped unexpectedly."
        } finally {
            runCatching { if (capturing) record.stop() }
            record.release()
            runCatching { out.flush(); out.close() }
            val reason = failure
            if (reason != null && running) {
                running = false
                _state.value = RecorderState.Error(reason)
            }
        }
    }
}
