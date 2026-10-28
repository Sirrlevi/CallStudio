package com.callstudio.app.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

class MicRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var startedAt: Long = 0L
    private var paused = false
    private var output: File? = null

    val isRecording: Boolean get() = recorder != null
    val isPaused: Boolean get() = paused

    fun start(): File {
        check(recorder == null) { "Recording is already active" }
        val dir = File(context.filesDir, "recordings").apply { mkdirs() }
        val file = File(dir, "mic_${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        try {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioSamplingRate(44_100)
            r.setAudioEncodingBitRate(96_000)
            r.setOutputFile(file.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            output = file
            startedAt = SystemClock.elapsedRealtime()
            paused = false
            return file
        } catch (e: Exception) {
            try { r.release() } catch (_: Exception) { }
            file.delete()
            throw e
        }
    }

    fun pause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recorder != null && !paused) {
            try { recorder?.pause(); paused = true } catch (_: Exception) { }
        }
    }

    fun resume() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && recorder != null && paused) {
            try { recorder?.resume(); paused = false } catch (_: Exception) { }
        }
    }

    fun stop(): Result {
        val r = recorder ?: error("No recording is active")
        val file = output ?: error("Missing recording output")
        val duration = ((SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0L))
        var valid = true
        try { r.stop() } catch (_: Exception) { valid = false }
        try { r.reset() } catch (_: Exception) { }
        try { r.release() } catch (_: Exception) { }
        recorder = null
        output = null
        paused = false
        if (!valid || !file.exists() || file.length() < 512L) {
            file.delete()
            error("Recording was too short or could not be saved")
        }
        return Result(file.absolutePath, duration)
    }

    fun abandon() {
        try { recorder?.stop() } catch (_: Exception) { }
        try { recorder?.release() } catch (_: Exception) { }
        recorder = null
        output?.delete()
        output = null
        paused = false
    }

    data class Result(val path: String, val durationMillis: Long)
}
