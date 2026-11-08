package com.callstudio.app.fakeoutgoing

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.callstudio.app.audio.AudioRouter
import com.callstudio.app.audio.VoicePlayer
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.preferences.SettingsStore
import com.callstudio.app.preferences.VoicePlayMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

enum class FakePhase { IDLE, DIALING, RINGING, CONNECTED, ENDED }

data class FakeCallRequest(
    val name: String,
    val number: String,
    val photoFile: String?,
    val country: String?,
    val voiceUri: String? = null,
    val voiceLabel: String? = null,
    val forcedOutcome: FakeOutcome? = null,
)

data class FakeOutgoingState(
    val phase: FakePhase = FakePhase.IDLE,
    val name: String = "",
    val number: String = "",
    val photoFile: String? = null,
    val country: String? = null,
    val answeredElapsed: Long = 0L,
    val endedLabel: String? = null,
    val muted: Boolean = false,
    val speakerOn: Boolean = false,
    val held: Boolean = false,
    val notice: String? = null,
)

/**
 * Plays out a staged outgoing call on this phone: calling, ringing, then one of several endings after at least 20
 * seconds. Nothing is dialled. When a clip was attached it starts by itself shortly after the "other side" answers.
 */
class FakeOutgoingController(
    private val app: Context,
    private val scope: CoroutineScope,
    private val settings: SettingsStore,
    private val random: Random = Random.Default,
) {
    private val _state = MutableStateFlow(FakeOutgoingState())
    val state: StateFlow<FakeOutgoingState> = _state.asStateFlow()

    private val tones = OutgoingTones()
    private val router = AudioRouter(app)
    private val voice = VoicePlayer(app, inCall = true)
    private var script: Job? = null
    private var clipWasPlayingBeforeHold = false

    /** Returns false when a fake call is already under way. */
    fun start(request: FakeCallRequest): Boolean {
        if (_state.value.phase != FakePhase.IDLE) return false
        val plan = FakeOutgoingPlanner.plan(random, request.forcedOutcome)
        _state.value = FakeOutgoingState(
            phase = FakePhase.DIALING,
            name = request.name,
            number = request.number,
            photoFile = request.photoFile,
            country = request.country,
        )
        script = scope.launch { play(request, plan) }
        return true
    }

    private suspend fun play(request: FakeCallRequest, plan: FakeOutgoingPlan) {
        val st = settings.current()
        router.enterCall()
        if (st.startOnSpeaker) router.setSpeaker(true)
        _state.update { it.copy(speakerOn = router.isSpeakerOn()) }

        tones.connecting()
        delay(plan.connectingMs)
        _state.update { it.copy(phase = FakePhase.RINGING) }
        tones.ringback()
        delay(plan.ringMs)

        when (plan.outcome) {
            FakeOutcome.ANSWERED -> answered(request, st.voiceMode == VoicePlayMode.AUTO_REPEAT)
            FakeOutcome.NO_ANSWER -> {
                tones.stop()
                tones.callEnded()
                finish("No answer")
            }
            FakeOutcome.REJECTED -> {
                tones.stop()
                tones.busy()
                delay(3_600)
                tones.stop()
                tones.callEnded()
                finish("Call declined")
            }
            FakeOutcome.BUSY -> {
                tones.stop()
                tones.busy()
                delay(4_200)
                tones.stop()
                tones.callEnded()
                finish("Number busy")
            }
            FakeOutcome.NETWORK_DROP -> {
                tones.stop()
                tones.networkFailure()
                finish("Call failed", extraHoldMs = 1_200)
            }
        }
    }

    private suspend fun answered(request: FakeCallRequest, repeat: Boolean) {
        tones.stop()
        tones.connected()
        buzz()
        _state.update { it.copy(phase = FakePhase.CONNECTED, answeredElapsed = SystemClock.elapsedRealtime()) }
        val uri = request.voiceUri ?: return
        delay(900)
        val playable = withContext(Dispatchers.IO) { voice.load(uri, repeat) }
        if (playable) voice.play() else _state.update { it.copy(notice = "The attached audio can't be played.") }
    }

    /** Shows how the call ended for a moment, then clears everything. */
    private suspend fun finish(label: String, extraHoldMs: Long = 0) {
        _state.update { it.copy(phase = FakePhase.ENDED, endedLabel = label) }
        delay(2_600 + extraHoldMs)
        cleanup()
    }

    private fun cleanup() {
        tones.stop()
        voice.release()
        router.exitCall()
        clipWasPlayingBeforeHold = false
        _state.value = FakeOutgoingState()
    }

    /** The user pressed End. */
    fun end() {
        val phase = _state.value.phase
        if (phase == FakePhase.IDLE || phase == FakePhase.ENDED) return
        script?.cancel()
        scope.launch {
            tones.stop()
            voice.pause()
            tones.callEnded()
            finish("Call ended")
        }
    }

    fun toggleMute() = _state.update { it.copy(muted = !it.muted) }

    fun toggleSpeaker() {
        if (_state.value.phase == FakePhase.IDLE || _state.value.phase == FakePhase.ENDED) return
        val want = !_state.value.speakerOn
        val ok = router.setSpeaker(want)
        val actual = router.isSpeakerOn()
        _state.update {
            it.copy(speakerOn = actual, notice = if (!ok || actual != want) "This device couldn't switch the audio output." else it.notice)
        }
    }

    /** Hold pauses the attached clip and resumes it where it left off. */
    fun toggleHold() {
        val s = _state.value
        if (s.phase != FakePhase.CONNECTED) return
        if (!s.held) {
            clipWasPlayingBeforeHold = voice.state.value == VoiceState.Playing
            if (clipWasPlayingBeforeHold) voice.pause()
        } else if (clipWasPlayingBeforeHold) {
            voice.play()
            clipWasPlayingBeforeHold = false
        }
        _state.update { it.copy(held = !s.held) }
    }

    fun clearNotice() = _state.update { it.copy(notice = null) }

    @Suppress("DEPRECATION")
    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            app.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) {
            vibrator.vibrate(VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }
}
