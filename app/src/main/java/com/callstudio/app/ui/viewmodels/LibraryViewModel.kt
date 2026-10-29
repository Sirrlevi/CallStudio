package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.audio.VoicePlayer
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.container
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.RecordingEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibraryViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val recordings: StateFlow<List<RecordingEntity>?> =
        c.recordings.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val history: StateFlow<List<CallEventEntity>?> =
        c.db.callDao().observeHistory().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val player = VoicePlayer(app, inCall = false)
    val playerState: StateFlow<VoiceState> = player.state

    var playingId by mutableLongStateOf(-1L)
        private set
    var notice by mutableStateOf<String?>(null)
        private set

    fun clearNotice() { notice = null }

    fun fileExists(r: RecordingEntity): Boolean = c.recordings.fileOf(r).exists()

    fun toggle(r: RecordingEntity) {
        viewModelScope.launch {
            if (playingId == r.id && player.state.value == VoiceState.Playing) {
                player.release()
                playingId = -1L
                return@launch
            }
            val file = c.recordings.fileOf(r)
            if (!file.exists()) {
                notice = "This recording's file is missing."
                return@launch
            }
            playingId = r.id
            val ok = withContext(Dispatchers.IO) { player.load(Uri.fromFile(file).toString(), repeat = false) }
            if (ok) player.play() else {
                playingId = -1L
                notice = "This recording can't be played."
            }
        }
    }

    fun delete(r: RecordingEntity) {
        viewModelScope.launch {
            if (playingId == r.id) {
                player.release()
                playingId = -1L
            }
            c.recordings.delete(r)
        }
    }

    fun clearHistory() {
        viewModelScope.launch { c.db.callDao().clearHistory() }
    }

    fun stopPlayback() {
        player.release()
        playingId = -1L
    }

    override fun onCleared() {
        player.release()
    }
}
