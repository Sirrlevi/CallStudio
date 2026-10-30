package com.callstudio.app.audio

import com.callstudio.app.data.CallDao

/**
 * Each call keeps its own voice file, so Android's persisted read access for a file is released once no
 * unfinished call needs it (and it isn't also the chosen custom ringtone).
 */
class VoiceLeases(
    private val calls: CallDao,
    private val reservedUri: suspend () -> String? = { null },
    private val release: (String) -> Unit,
) {
    suspend fun releaseIfUnused(uri: String?) {
        if (uri.isNullOrBlank()) return
        if (uri == reservedUri()) return
        if (calls.countActiveWithVoice(uri) == 0) release(uri)
    }
}
