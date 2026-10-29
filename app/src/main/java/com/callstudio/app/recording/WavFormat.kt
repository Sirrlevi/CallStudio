package com.callstudio.app.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder

object WavFormat {
    const val HEADER_SIZE = 44

    fun header(dataBytes: Long, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val buf = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray(Charsets.US_ASCII))
        buf.putInt((36 + dataBytes).toInt())
        buf.put("WAVE".toByteArray(Charsets.US_ASCII))
        buf.put("fmt ".toByteArray(Charsets.US_ASCII))
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(byteRate)
        buf.putShort((channels * bitsPerSample / 8).toShort())
        buf.putShort(bitsPerSample.toShort())
        buf.put("data".toByteArray(Charsets.US_ASCII))
        buf.putInt(dataBytes.toInt())
        return buf.array()
    }

    fun durationMs(dataBytes: Long, sampleRate: Int, channels: Int = 1, bitsPerSample: Int = 16): Long {
        val bytesPerSecond = sampleRate.toLong() * channels * bitsPerSample / 8
        return if (bytesPerSecond <= 0) 0 else dataBytes * 1000L / bytesPerSecond
    }
}
