package com.callstudio.app.fakeout

import android.content.Context
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.callstudio.app.audio.AudioRouter
import com.callstudio.app.audio.VoicePlayer
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.domain.CallTimerOffset
import com.callstudio.app.domain.Countries
import com.callstudio.app.preferences.SettingsStore
import com.callstudio.app.preferences.VoicePlayMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.random.Random

/** Everything the fake call screen needs to know about the number being "called". */
data class FakeCallRequest(
    val callerId: Long?,
    /** Blank when a bare number was dialed. */
    val name: String,
    /** Shown number, for example "+91 80590 56588" or "070820 10305". */
    val number: String,
    /** Digits used for the dial tones. */
    val dialDigits: String,
    val iso: String?,
    val countryName: String?,
    val photoFile: String?,
    val voiceUri: String?,
    val voiceLabel: String?,
    val settings: FakeOutgoingSettings,
) {
    val title: String get() = name.ifBlank { number }
}

enum class FakePhase {
    IDLE,

    /** "Calling…": dialing, connecting to the network. */
    CONNECTING,

    /** "Ringing…": the other phone is ringing. */
    RINGING,

    /** Picked up: timer running, attached audio playing. */
    CONNECTED,

    /** "Call ended", shown for a moment before the screen closes. */
    ENDED,
}

data class FakeCallState(
    val phase: FakePhase = FakePhase.IDLE,
    val request: FakeCallRequest? = null,
    /** The clock base of the call timer: it shows `elapsedRealtime() - connectedElapsed` (see "Ongoing call timer"). */
    val connectedElapsed: Long = 0L,
    /** SystemClock.elapsedRealtime() when the other side really picked up, so a normal timer can always be restored. */
    val realConnectedElapsed: Long = 0L,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val held: Boolean = false,
    /** Small reason line on the "Call ended" screen, for example "Number busy". */
    val endNote: String? = null,
    val voice: VoiceState = VoiceState.NoClip,
) {
    val isLive: Boolean get() = phase != FakePhase.IDLE
    val inCall: Boolean get() = phase == FakePhase.CONNECTING || phase == FakePhase.RINGING || phase == FakePhase.CONNECTED
}

enum class FakeStartResult { STARTED, ALREADY_IN_CALL, OTHER_CALL_LIVE }

/**
 * Runs one fake outgoing call at a time: CONNECTING -> RINGING -> (CONNECTED | ENDED).
 *
 * The ending is picked by [FakeCallPlanner] before the call starts; this class only plays that timeline out with
 * sounds and state. Hanging up works at every point because every wait goes through [sleep], which wakes the
 * moment the user taps End. Nothing here dials, contacts or records anyone.
 */
class FakeOutgoingEngine(
    private val app: Context,
    private val scope: CoroutineScope,
    private val prefs: FakeOutgoingPrefs,
    private val appSettings: SettingsStore,
    /** True while a (fake incoming) call is ringing or active, so two calls never overlap. */
    private val otherCallLive: () -> Boolean,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow(FakeCallState())
    val state: StateFlow<FakeCallState> = _state.asStateFlow()

    private val tones = TonePlayer(app)
    private val voice = VoicePlayer(app, inCall = true)
    private val router = AudioRouter(app)
    private val operator = OperatorVoice(app)

    private var job: Job? = null
    private var userEnded = CompletableDeferred<Unit>()
    private var heldClipWasPlaying = false

    init {
        scope.launch { voice.state.collect { v -> _state.update { it.copy(voice = v) } } }
    }

    // ---- control ------------------------------------------------------------------------------------------

    /**
     * "Ongoing call timer" saved in Settings while a fake outgoing call is connected: the timer reads that time right
     * now and keeps running (0 = the real time since the call connected).
     */
    fun setLiveTimer(seconds: Long) {
        _state.update {
            if (it.phase != FakePhase.CONNECTED) it
            else it.copy(connectedElapsed = CallTimerOffset.liveBase(SystemClock.elapsedRealtime(), it.realConnectedElapsed, seconds))
        }
    }

    fun start(request: FakeCallRequest): FakeStartResult {
        if (_state.value.phase != FakePhase.IDLE) return FakeStartResult.ALREADY_IN_CALL
        if (otherCallLive()) return FakeStartResult.OTHER_CALL_LIVE
        val s = request.settings
        val plan = FakeCallPlanner.plan(
            random = random,
            limits = FakeCallLimits(strictMinimum = !s.earlyBusyAndDrop),
            previous = prefs.lastOutcome(),
            forced = s.forced,
        )
        prefs.setLastOutcome(plan.outcome)
        userEnded = CompletableDeferred()
        heldClipWasPlaying = false
        _state.value = FakeCallState(phase = FakePhase.CONNECTING, request = request)
        job = scope.launch { run(request, plan) }
        return FakeStartResult.STARTED
    }

    /** The End button. Works while calling, ringing and connected. */
    fun end() {
        if (_state.value.inCall) userEnded.complete(Unit)
    }

    fun toggleMute() {
        if (!_state.value.inCall) return
        _state.update { it.copy(muted = !it.muted) }
    }

    fun toggleSpeaker() {
        if (!_state.value.inCall) return
        val want = !_state.value.speakerOn
        router.setSpeaker(want)
        _state.update { it.copy(speakerOn = router.isSpeakerOn()) }
    }

    /** Hold pauses the attached audio and resumes exactly what was playing. */
    fun toggleHold() {
        if (_state.value.phase != FakePhase.CONNECTED) return
        if (!_state.value.held) {
            heldClipWasPlaying = voice.state.value == VoiceState.Playing
            if (heldClipWasPlaying) voice.pause()
            _state.update { it.copy(held = true) }
        } else {
            if (heldClipWasPlaying) voice.play()
            heldClipWasPlaying = false
            _state.update { it.copy(held = false) }
        }
    }

    // ---- the timeline -------------------------------------------------------------------------------------

    private data class Ending(val lingerMs: Long)

    private enum class Step { DONE, SKIPPED, USER_ENDED }

    private suspend fun run(req: FakeCallRequest, plan: FakeCallPlan) {
        val ending = try {
            coroutineScope {
                val settings = appSettings.current()
                val region = ToneSynth.regionFor(req.iso ?: Countries.defaultIso())

                router.enterCall()
                if (settings.startOnSpeaker) router.setSpeaker(true)
                _state.update { it.copy(speakerOn = router.isSpeakerOn()) }
                tones.requestFocus()

                val needsAnnouncement = req.settings.announcements && plan.announce &&
                    (plan.outcome == FakeOutcome.BUSY || plan.outcome == FakeOutcome.NO_ANSWER || plan.outcome == FakeOutcome.REJECTED)
                // Prepared while it is still "Calling…", so a spoken message is ready the moment it is needed.
                val announcer: Deferred<Boolean>? =
                    if (needsAnnouncement) async { runCatching { operator.prepare() }.getOrDefault(false) } else null
                val clipReady: Deferred<Boolean>? =
                    if (plan.outcome == FakeOutcome.ANSWERED && !req.voiceUri.isNullOrBlank()) {
                        async(Dispatchers.IO) {
                            runCatching {
                                voice.load(req.voiceUri, repeat = settings.voiceMode == VoicePlayMode.AUTO_REPEAT)
                            }.getOrDefault(false)
                        }
                    } else null
                try {
                    timeline(req, plan, region, announcer, clipReady)
                } finally {
                    announcer?.cancel()
                    clipReady?.cancel()
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "The fake call failed", t)
            Ending(lingerMs = 800L)
        }
        finish(ending)
    }

    /** Waits [ms], or less when the user hangs up. Returns true when the user hung up. */
    private suspend fun sleep(ms: Long): Boolean =
        withTimeoutOrNull(ms.coerceAtLeast(1L)) { userEnded.await() } != null

    private fun hungUp() = Ending(lingerMs = 1_100L)

    private suspend fun timeline(
        req: FakeCallRequest,
        plan: FakeCallPlan,
        region: ToneRegion,
        announcer: Deferred<Boolean>?,
        clipReady: Deferred<Boolean>?,
    ): Ending {
        // 1. "Calling…": a beat of silence, then the key tones of the number, then the network connecting.
        var spent = 0L
        if (req.settings.dialTones) {
            val keys = ToneSynth.dialSequence(req.dialDigits)
            if (keys.isNotEmpty()) {
                if (sleep(300L)) return hungUp()
                tones.play(keys)
                val keysMs = ToneSynth.durationMs(keys)
                spent = 300L + keysMs
                if (sleep(keysMs)) return hungUp()
            }
        }
        if (sleep((plan.connectMs - spent).coerceAtLeast(500L))) return hungUp()

        // 2. "Ringing…": ringback until the outcome happens.
        _state.update { it.copy(phase = FakePhase.RINGING) }
        tones.startLoop(ToneSynth.render(ToneSynth.ringback(region)))
        if (sleep(plan.ringMs)) return hungUp()
        tones.stopLoop()

        // 3. The outcome.
        return when (plan.outcome) {
            FakeOutcome.ANSWERED -> answered(req, plan, clipReady)
            FakeOutcome.NO_ANSWER -> {
                markEnded(null)
                endWithMessage(AnnouncementKind.NOT_ANSWERING, region, announcer, fallback = ToneSynth.cycles(ToneSynth.reorder(region), 2))
            }
            FakeOutcome.BUSY -> {
                markEnded("Number busy")
                endWithMessage(AnnouncementKind.LINE_BUSY, region, announcer, fallback = ToneSynth.cycles(ToneSynth.busy(region), 3))
            }
            FakeOutcome.REJECTED -> {
                markEnded(null)
                if (sleep(350L)) {
                    Ending(lingerMs = 300L)
                } else {
                    endWithMessage(AnnouncementKind.LINE_BUSY, region, announcer, fallback = ToneSynth.cycles(ToneSynth.busy(region), 2))
                }
            }
            FakeOutcome.NETWORK_DROP -> {
                // The line breaks up, goes dead, then the fast "tu-tu-tu" of a failed call.
                tones.play(ToneSynth.lineCrackle())
                if (sleep(520L)) return hungUp()
                markEnded("Network error")
                val failTone = ToneSynth.cycles(ToneSynth.reorder(region), 4)
                tones.play(failTone)
                sleep(ToneSynth.durationMs(failTone))
                Ending(lingerMs = 1_200L)
            }
        }
    }

    private suspend fun answered(req: FakeCallRequest, plan: FakeCallPlan, clipReady: Deferred<Boolean>?): Ending {
        tones.play(ToneSynth.connectPing())
        if (req.settings.vibrateOnAnswer) buzz()
        // "Ongoing call timer" (Settings): the timer starts at that time instead of 0:00 and keeps running.
        val head = try {
            CallTimerOffset.toMillis(appSettings.current().callTimerOffsetSeconds)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Exception) {
            0L
        }
        val pickedUp = SystemClock.elapsedRealtime()
        _state.update { it.copy(phase = FakePhase.CONNECTED, connectedElapsed = pickedUp - head, realConnectedElapsed = pickedUp) }
        // The other person says "Hello?" before anything is heard from them.
        if (sleep(plan.voiceDelayMs)) return hungUp()
        if (clipReady?.await() == true) voice.play()
        // The call stays up until the user hangs up. The clip ending never ends it.
        userEnded.await()
        return hungUp()
    }

    /** The call is over from the network's side: the screen says "Call ended" while the end sound plays. */
    private fun markEnded(note: String?) {
        _state.update { it.copy(phase = FakePhase.ENDED, endNote = note, held = false) }
    }

    private suspend fun endWithMessage(
        kind: AnnouncementKind,
        region: ToneRegion,
        announcer: Deferred<Boolean>?,
        fallback: ShortArray,
    ): Ending {
        if (sleep(450L)) return Ending(lingerMs = 300L)
        when (speak(announcer, Announcements.pick(region, kind, random))) {
            Step.USER_ENDED -> return Ending(lingerMs = 300L)
            Step.DONE -> return Ending(lingerMs = 900L)
            Step.SKIPPED -> Unit
        }
        tones.play(fallback)
        sleep(ToneSynth.durationMs(fallback))
        return Ending(lingerMs = 900L)
    }

    private suspend fun speak(announcer: Deferred<Boolean>?, announcement: Announcement): Step {
        if (announcer == null || !announcer.await()) return Step.SKIPPED
        val done = operator.speak(announcement)
        val deadline = SystemClock.elapsedRealtime() + SPEECH_LIMIT_MS
        while (!done.isCompleted) {
            if (userEnded.isCompleted) {
                operator.stop()
                return Step.USER_ENDED
            }
            if (SystemClock.elapsedRealtime() > deadline) {
                operator.stop()
                return Step.DONE
            }
            delay(80L)
        }
        return if (done.await()) Step.DONE else Step.SKIPPED
    }

    private suspend fun finish(ending: Ending) {
        tones.stopAll()
        operator.shutdown()
        voice.release()
        router.exitCall()
        tones.abandonFocus()
        _state.update { it.copy(phase = FakePhase.ENDED, held = false, muted = false) }
        delay(ending.lingerMs)
        _state.value = FakeCallState()
    }

    private fun buzz() {
        runCatching {
            val vibrator = app.getSystemService(Vibrator::class.java)
            vibrator?.vibrate(VibrationEffect.createOneShot(70L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private companion object {
        const val TAG = "FakeOutgoing"
        const val SPEECH_LIMIT_MS = 12_000L
    }
}
