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
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.fakeout.FakeCallRequest
import com.callstudio.app.fakeout.FakeCallState
import com.callstudio.app.fakeout.FakeOutgoingSettings
import com.callstudio.app.fakeout.FakeStartResult
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
    /** A saved caller (chosen the same way as for an incoming call); null when a number is typed instead. */
    val callerId: Long? = null,
    val iso: String = Countries.defaultIso(),
    val number: String = "",
    val voiceUri: String? = null,
    val voiceLabel: String? = null,
    val settings: FakeOutgoingSettings = FakeOutgoingSettings(),
    val error: String? = null,
) {
    val typedDigits: String get() = number.filter { it.isDigit() }
    val canCall: Boolean get() = callerId != null || typedDigits.length >= 3
}

/** Picks who to "call" and an optional audio file, then hands the call over to the fake outgoing engine. */
class FakeOutgoingViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ctx: Application = app

    private val _ui = MutableStateFlow(FakeOutgoingUi(settings = c.fakeOutgoingPrefs.load()))
    val ui: StateFlow<FakeOutgoingUi> = _ui.asStateFlow()

    /** Null while loading. */
    val callers: StateFlow<List<CallerEntity>?> =
        c.callers.all.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val callState: StateFlow<FakeCallState> = c.fakeOutgoing.state

    fun selectCaller(id: Long) = _ui.update { it.copy(callerId = id, error = null) }
    fun clearCaller() = _ui.update { it.copy(callerId = null) }
    fun setCountry(iso: String) = _ui.update { it.copy(iso = iso) }

    fun setNumber(text: String) = _ui.update {
        it.copy(number = text.filter { ch -> ch.isDigit() || ch == ' ' || ch == '+' || ch == '-' }.take(30), error = null)
    }

    fun updateSettings(change: (FakeOutgoingSettings) -> FakeOutgoingSettings) {
        val next = change(_ui.value.settings)
        c.fakeOutgoingPrefs.save(next)
        _ui.update { it.copy(settings = next) }
    }

    // ---- audio played after pickup -------------------------------------------------------------------------

    fun acceptClip(uri: Uri) {
        viewModelScope.launch {
            AudioFiles.persist(ctx, uri)
            val playable = withContext(Dispatchers.IO) { AudioFiles.isPlayable(ctx, uri) }
            if (!playable) {
                c.voiceLeases.releaseIfUnused(uri.toString())
                _ui.update { it.copy(error = "That file can't be played. Use an MP3, M4A, WAV, OGG or FLAC file.") }
                return@launch
            }
            val old = _ui.value.voiceUri
            val label = AudioFiles.displayName(ctx, uri) ?: "Audio file"
            _ui.update { it.copy(voiceUri = uri.toString(), voiceLabel = label, error = null) }
            if (old != null && old != uri.toString()) c.voiceLeases.releaseIfUnused(old)
        }
    }

    fun removeClip() {
        val old = _ui.value.voiceUri
        _ui.update { it.copy(voiceUri = null, voiceLabel = null) }
        if (old != null) viewModelScope.launch { c.voiceLeases.releaseIfUnused(old) }
    }

    // ---- the call ------------------------------------------------------------------------------------------

    /** Starts the fake call. [onStarted] runs once the engine accepted it, to open the call screen. */
    fun start(onStarted: () -> Unit) {
        val u = _ui.value
        viewModelScope.launch {
            val request = buildRequest(u)
            if (request == null) {
                _ui.update { it.copy(error = "Choose a contact or type a number first.") }
                return@launch
            }
            when (c.fakeOutgoing.start(request)) {
                FakeStartResult.STARTED, FakeStartResult.ALREADY_IN_CALL -> onStarted()
                FakeStartResult.OTHER_CALL_LIVE ->
                    _ui.update { it.copy(error = "Another call is ringing or in progress. End it first.") }
            }
        }
    }

    private suspend fun buildRequest(u: FakeOutgoingUi): FakeCallRequest? {
        val caller = u.callerId?.let { c.callers.get(it) }
        if (caller != null) {
            c.settings.setLastCaller(caller.id)
            return FakeCallRequest(
                callerId = caller.id,
                name = caller.name,
                number = caller.displayNumber(),
                dialDigits = caller.phone.filter { it.isDigit() },
                iso = caller.countryIso.ifBlank { null },
                countryName = null,
                photoFile = caller.photoFile,
                voiceUri = u.voiceUri,
                voiceLabel = u.voiceLabel,
                settings = u.settings,
            )
        }
        if (u.typedDigits.length < 3) return null
        val parsed = PhoneNumbers.normalize(u.number, u.iso)
        val typed = u.number.trim()
        // What was typed is what is shown (spaces included); a plain run of digits is grouped like a phone would.
        val shown = if (typed.contains(' ') || typed.startsWith("+")) typed else PhoneNumbers.group(parsed.national, parsed.iso)
        return FakeCallRequest(
            callerId = null,
            name = "",
            number = shown,
            dialDigits = parsed.national.filter { it.isDigit() },
            iso = parsed.iso,
            countryName = Countries.nameOf(parsed.iso),
            photoFile = null,
            voiceUri = u.voiceUri,
            voiceLabel = u.voiceLabel,
            settings = u.settings,
        )
    }

    fun clearError() = _ui.update { it.copy(error = null) }
}
