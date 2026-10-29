package com.callstudio.app.data

import android.content.Context
import com.callstudio.app.recording.WavFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

class RecordingRepository(private val context: Context, private val dao: RecordingDao) {
    val all: Flow<List<RecordingEntity>> = dao.observeAll()

    fun dir(): File = File(context.filesDir, "recordings").apply { mkdirs() }

    fun fileOf(r: RecordingEntity): File = File(dir(), r.fileName)

    suspend fun add(file: File, durationMs: Long, sampleRate: Int, callerId: Long?, callerName: String): Long =
        dao.insert(
            RecordingEntity(
                fileName = file.name,
                createdAt = System.currentTimeMillis(),
                durationMs = durationMs,
                sizeBytes = file.length(),
                sampleRate = sampleRate,
                callerId = callerId,
                callerName = callerName,
            )
        )

    suspend fun delete(r: RecordingEntity) {
        withContext(Dispatchers.IO) { runCatching { fileOf(r).delete() } }
        dao.delete(r.id)
    }

    /**
     * If the process died mid-recording (for example the user revoked the microphone permission in Settings),
     * the WAV header was never finalised. Repair such files so the audio captured so far is not lost.
     */
    suspend fun recoverOrphans() = withContext(Dispatchers.IO) {
        val known = dao.getAll().map { it.fileName }.toSet()
        dir().listFiles()?.filter { it.name.endsWith(".wav") && it.name !in known && it.length() > WavFormat.HEADER_SIZE }
            ?.forEach { f ->
                val rate = f.name.removeSuffix(".wav").substringAfterLast('_').toIntOrNull() ?: 16_000
                val dataBytes = f.length() - WavFormat.HEADER_SIZE
                runCatching {
                    RandomAccessFile(f, "rw").use { raf ->
                        raf.seek(0)
                        raf.write(WavFormat.header(dataBytes, rate))
                    }
                    dao.insert(
                        RecordingEntity(
                            fileName = f.name,
                            createdAt = f.lastModified(),
                            durationMs = WavFormat.durationMs(dataBytes, rate),
                            sizeBytes = f.length(),
                            sampleRate = rate,
                            callerId = null,
                            callerName = "Recovered recording",
                        )
                    )
                }
            }
    }
}
