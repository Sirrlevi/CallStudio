package com.callstudio.app.diag

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A small on-device log of what happened when a call fired (alarm, notification, ringing). It exists so that when a
 * call arrives silently the reason can be read in Settings > Permissions and setup instead of guessed.
 * It never stores names, numbers or photos: only event ids and system states.
 */
object CallDiagnostics {
    private const val TAG = "CallReport"
    private const val FILE = "call-report.log"
    private const val MAX_BYTES = 48 * 1024
    private val lock = Any()

    @Volatile
    private var dir: File? = null

    fun init(context: Context) {
        dir = context.applicationContext.filesDir
    }

    fun log(step: String, detail: String = "") {
        Log.i(TAG, if (detail.isBlank()) step else "$step - $detail")
        val folder = dir ?: return
        val line = buildString {
            append(SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault()).format(Date()))
            append("  ").append(step)
            if (detail.isNotBlank()) append(": ").append(detail)
        }
        synchronized(lock) {
            runCatching {
                val file = File(folder, FILE)
                if (file.length() > MAX_BYTES) {
                    val kept = file.readLines().let { it.takeLast(it.size / 2) }
                    file.writeText(kept.joinToString("\n", postfix = "\n"))
                }
                file.appendText(line + "\n")
            }
        }
    }

    /** Newest line first, at most [limit] lines. */
    fun recent(limit: Int = 40): List<String> {
        val folder = dir ?: return emptyList()
        return synchronized(lock) {
            runCatching { File(folder, FILE).readLines().takeLast(limit).reversed() }.getOrDefault(emptyList())
        }
    }

    fun clear() {
        val folder = dir ?: return
        synchronized(lock) { runCatching { File(folder, FILE).delete() } }
    }
}
