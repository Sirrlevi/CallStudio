package com.callstudio.app.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.callstudio.app.audio.AudioFiles
import com.callstudio.app.container
import com.callstudio.app.contacts.ContactsHelper
import com.callstudio.app.domain.Countries
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.outgoing.ClipUploader
import com.callstudio.app.outgoing.OutgoingException
import com.callstudio.app.outgoing.OutgoingSettings
import com.callstudio.app.outgoing.TwilioClient
import com.callstudio.app.outgoing.TwilioConfig
import com.callstudio.app.outgoing.TwimlBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class OutgoingPhase { IDLE, UPLOADING, DIALING, ACTIVE, FINISHED }

/** [uri] is a file on this phone (uploaded when you call); [url] is the public link Twilio will play. */
data class ClipItem(val id: Long, val label: String, val uri: String?, val url: String?)

data class OutgoingUi(
    val iso: String = Countries.defaultIso(),
    val number: String = "",
    val clips: List<ClipItem> = emptyList(),
    val connectLive: Boolean = false,
    val phase: OutgoingPhase = OutgoingPhase.IDLE,
    val statusText: String? = null,
    val progressText: String? = null,
    val error: String? = null,
    val callSid: String? = null,
) {
    val busy: Boolean get() = phase == OutgoingPhase.UPLOADING || phase == OutgoingPhase.DIALING || phase == OutgoingPhase.ACTIVE
}

class OutgoingViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container
    private val ctx: Application = app
    private val _ui = MutableStateFlow(OutgoingUi())
    val ui: StateFlow<OutgoingUi> = _ui.asStateFlow()
    val settings: StateFlow<OutgoingSettings?> =
        c.outgoingSettings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private var job: Job? = null
    private var nextId = 1L

    fun setNumber(text: String) = _ui.update {
        it.copy(number = text.filter { ch -> ch.isDigit() || ch == ' ' || ch == '+' || ch == '-' }.take(30), error = null)
    }

    fun setCountry(iso: String) = _ui.update { it.copy(iso = iso) }
    fun setConnectLive(on: Boolean) = _ui.update { it.copy(connectLive = on) }

    fun pickContact(uri: Uri) {
        viewModelScope.launch {
            val info = withContext(Dispatchers.IO) { ContactsHelper.read(ctx, uri) }
            if (info == null) {
                _ui.update { it.copy(error = "That contact couldn't be read.") }
                return@launch
            }
            val parsed = PhoneNumbers.normalize(info.number, _ui.value.iso)
            _ui.update { it.copy(iso = parsed.iso, number = PhoneNumbers.group(parsed.national, parsed.iso), error = null) }
        }
    }

    // ---- clips -------------------------------------------------------------------------------------------

    fun addClipFile(uri: Uri) {
        viewModelScope.launch {
            AudioFiles.persist(ctx, uri)
            val playable = withContext(Dispatchers.IO) { AudioFiles.isPlayable(ctx, uri) }
            if (!playable) {
                AudioFiles.release(ctx, uri.toString())
                _ui.update { it.copy(error = "That file can't be played. Use an MP3, M4A, WAV, OGG or FLAC file.") }
                return@launch
            }
            val label = AudioFiles.displayName(ctx, uri) ?: "Audio file"
            _ui.update { it.copy(clips = it.clips + ClipItem(nextId++, label, uri.toString(), null), error = null) }
        }
    }

    fun addClipLink(link: String) {
        val clean = link.trim()
        if (!clean.startsWith("https://") || clean.length < 12 || clean.any { it.isWhitespace() }) {
            _ui.update { it.copy(error = "Enter a full link that starts with https://") }
            return
        }
        _ui.update { it.copy(clips = it.clips + ClipItem(nextId++, clean.substringAfterLast('/').ifBlank { clean }, null, clean), error = null) }
    }

    fun removeClip(id: Long) {
        val clip = _ui.value.clips.firstOrNull { it.id == id } ?: return
        _ui.update { it.copy(clips = it.clips.filterNot { c -> c.id == id }) }
        clip.uri?.let { AudioFiles.release(ctx, it) }
    }

    fun moveClip(id: Long, delta: Int) = _ui.update { state ->
        val list = state.clips.toMutableList()
        val from = list.indexOfFirst { it.id == id }
        val to = from + delta
        if (from < 0 || to !in list.indices) state else {
            list.add(to, list.removeAt(from))
            state.copy(clips = list)
        }
    }

    // ---- the call ----------------------------------------------------------------------------------------

    fun start() {
        val s = settings.value
        val u = _ui.value
        if (u.busy) return
        val problem = validate(s, u)
        if (problem != null || s == null) {
            _ui.update { it.copy(error = problem, phase = OutgoingPhase.IDLE) }
            return
        }
        val parsed = PhoneNumbers.normalize(u.number, u.iso)
        val to = PhoneNumbers.e164(parsed.iso, parsed.national) ?: return
        job = viewModelScope.launch {
            try {
                val twilio = TwilioClient(TwilioConfig(s.accountSid.trim(), s.authToken.trim(), s.fromNumber.trim()))
                _ui.update { it.copy(phase = OutgoingPhase.UPLOADING, error = null, statusText = null, callSid = null) }
                val urls = uploadClips(s)
                _ui.update { it.copy(phase = OutgoingPhase.DIALING, progressText = null, statusText = "Calling…") }
                val script = TwimlBuilder.build(urls, if (u.connectLive) s.myNumber.trim() else null)
                // The request to Twilio can't be interrupted half-way. If Cancel is pressed while it is in flight the call
                // already exists on Twilio's side, so it is ended right away instead of being left running unseen.
                val me = coroutineContext.job
                val sid = withContext(NonCancellable) {
                    val created = twilio.startCall(to, script)
                    if (me.isCancelled) {
                        runCatching { twilio.hangUp(created) }
                        null
                    } else {
                        created
                    }
                } ?: return@launch
                _ui.update { it.copy(phase = OutgoingPhase.ACTIVE, callSid = sid) }
                watch(twilio, sid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: OutgoingException) {
                _ui.update { it.copy(phase = OutgoingPhase.IDLE, progressText = null, error = e.message) }
            } catch (e: Exception) {
                _ui.update { it.copy(phase = OutgoingPhase.IDLE, progressText = null, error = "Something went wrong. Try again.") }
            }
        }
    }

    private fun validate(s: OutgoingSettings?, u: OutgoingUi): String? {
        val parsed = PhoneNumbers.normalize(u.number, u.iso)
        return when {
            s == null || !s.twilioReady -> "Add your Twilio details in the setup first."
            PhoneNumbers.e164(parsed.iso, parsed.national) == null -> "Enter the full phone number."
            u.clips.isEmpty() && !u.connectLive -> "Add at least one clip, or turn on joining the call yourself."
            u.connectLive && !PhoneNumbers.isE164(s.myNumber) -> "Add your own phone number in the setup (with + and country code) to join live."
            u.clips.any { it.url == null } && !s.uploadReady ->
                "Add your upload details in the setup, or add clips as links instead of files."
            else -> null
        }
    }

    private suspend fun uploadClips(s: OutgoingSettings): List<String> {
        val urls = mutableListOf<String>()
        val total = _ui.value.clips.size
        _ui.value.clips.forEachIndexed { index, clip ->
            val existing = clip.url
            val url = if (existing != null) existing else {
                _ui.update { it.copy(progressText = "Uploading clip ${index + 1} of $total…") }
                val uploaded = ClipUploader(ctx, s.cloudName.trim(), s.uploadPreset.trim()).upload(Uri.parse(clip.uri!!))
                _ui.update { state -> state.copy(clips = state.clips.map { if (it.id == clip.id) it.copy(url = uploaded) else it }) }
                uploaded
            }
            urls += url
        }
        return urls
    }

    /** Follows the call until it finishes. The call itself keeps running in the cloud if this screen goes away. */
    private suspend fun watch(twilio: TwilioClient, sid: String) {
        var failures = 0
        while (true) {
            try {
                val status = twilio.status(sid)
                failures = 0
                _ui.update { it.copy(statusText = status.label) }
                if (status.finished) {
                    _ui.update { it.copy(phase = OutgoingPhase.FINISHED) }
                    return
                }
            } catch (e: OutgoingException) {
                failures++
                if (failures >= 4) {
                    _ui.update {
                        it.copy(phase = OutgoingPhase.FINISHED, statusText = "Lost contact with Twilio. The call may still be running.", error = e.message)
                    }
                    return
                }
            }
            delay(2_000)
        }
    }

    /** Ends the call from Twilio's side, or cancels while clips are still uploading. */
    fun end() {
        val u = _ui.value
        when (u.phase) {
            OutgoingPhase.UPLOADING, OutgoingPhase.DIALING -> {
                job?.cancel()
                _ui.update { it.copy(phase = OutgoingPhase.IDLE, progressText = null, statusText = null) }
            }
            OutgoingPhase.ACTIVE -> {
                val sid = u.callSid ?: return
                val s = settings.value ?: return
                viewModelScope.launch {
                    try {
                        TwilioClient(TwilioConfig(s.accountSid.trim(), s.authToken.trim(), s.fromNumber.trim())).hangUp(sid)
                    } catch (e: OutgoingException) {
                        _ui.update { it.copy(error = e.message) }
                    }
                }
            }
            else -> Unit
        }
    }

    fun reset() = _ui.update { it.copy(phase = OutgoingPhase.IDLE, statusText = null, progressText = null, error = null, callSid = null) }

    fun clearError() = _ui.update { it.copy(error = null) }

    // ---- setup -------------------------------------------------------------------------------------------

    suspend fun saveSettings(value: OutgoingSettings) = c.outgoingSettings.save(value)
    suspend fun clearSettings() = c.outgoingSettings.clear()

    override fun onCleared() {
        job?.cancel()
    }
}
