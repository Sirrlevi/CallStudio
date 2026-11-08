package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.audio.AudioFiles
import com.callstudio.app.container
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.data.displayNumber
import com.callstudio.app.domain.Countries
import com.callstudio.app.fakeoutgoing.FakeCallRequest
import com.callstudio.app.fakeoutgoing.FakeOutcome
import com.callstudio.app.preferences.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FakeOutgoingUi(
    val callerId: Long = -1L,
    val clipUri: String? = null,
    val clipLabel: String? = null,
    /** null = let it be random. */
    val outcome: FakeOutcome? = null,
    val error: String? = null,
)

class FakeOutgoingViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ctx: Application = app

    private val _ui = MutableStateFlow(FakeOutgoingUi())
    val ui: StateFlow<FakeOutgoingUi> = _ui.asStateFlow()

    val callers: StateFlow<List<CallerEntity>?> =
        c.callers.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val settings: StateFlow<AppSettings> = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val call = c.fakeOutgoing.state
    val liveIncoming = c.sessions.session

    fun selectCaller(id: Long) = _ui.update { it.copy(callerId = id, error = null) }
    fun setOutcome(outcome: FakeOutcome?) = _ui.update { it.copy(outcome = outcome) }
    fun clearError() = _ui.update { it.copy(error = null) }

    /** Keeps access to the chosen audio file and checks Android can play it. */
    fun attachClip(uri: Uri) {
        viewModelScope.launch {
            AudioFiles.persist(ctx, uri)
            val playable = withContext(Dispatchers.IO) { AudioFiles.isPlayable(ctx, uri) }
            if (!playable) {
                c.voiceLeases.releaseIfUnused(uri.toString())
                _ui.update { it.copy(error = "That file can't be played. Use an MP3, M4A, WAV, OGG or FLAC file.") }
                return@launch
            }
            val old = _ui.value.clipUri
            _ui.update { it.copy(clipUri = uri.toString(), clipLabel = AudioFiles.displayName(ctx, uri) ?: "Audio file", error = null) }
            if (old != null && old != uri.toString()) c.voiceLeases.releaseIfUnused(old)
        }
    }

    fun removeClip() {
        val old = _ui.value.clipUri
        _ui.update { it.copy(clipUri = null, clipLabel = null) }
        viewModelScope.launch { c.voiceLeases.releaseIfUnused(old) }
    }

    /** Returns true when the staged call has started (or is already running). */
    fun start(caller: CallerEntity): Boolean {
        if (c.sessions.session.value.isLive) {
            _ui.update { it.copy(error = "A call is in progress.") }
            return false
        }
        val u = _ui.value
        val started = c.fakeOutgoing.start(
            FakeCallRequest(
                name = caller.name,
                number = caller.displayNumber(),
                photoFile = caller.photoFile,
                country = Countries.nameOf(caller.countryIso),
                voiceUri = u.clipUri,
                voiceLabel = u.clipLabel,
                forcedOutcome = u.outcome,
            )
        )
        if (started) {
            viewModelScope.launch {
                c.settings.setLastCaller(caller.id)
                c.callers.touch(caller.id)
            }
        }
        return true
    }
}
