package com.callstudio.app.session

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.callstudio.app.audio.AudioRouter
import com.callstudio.app.audio.VoiceLeases
import com.callstudio.app.audio.RingtonePlayer
import com.callstudio.app.audio.VoicePlayer
import com.callstudio.app.audio.VoiceState
import com.callstudio.app.data.CallDao
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.RecordingRepository
import com.callstudio.app.diag.CallDiagnostics
import com.callstudio.app.domain.CallEvent
import com.callstudio.app.domain.CallTimerOffset
import com.callstudio.app.domain.CallStateMachine
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.notifications.NotificationHelper
import com.callstudio.app.permissions.DoNotDisturb
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.preferences.SettingsStore
import com.callstudio.app.preferences.VoicePlayMode
import com.callstudio.app.recording.CallRecorder
import com.callstudio.app.recording.RecorderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Owns the one live call: SCHEDULED -> RINGING -> ACTIVE -> ENDED (or REJECTED / MISSED / CANCELLED / ERROR).
 * All state changes run under one mutex, so repeated taps, notification buttons and timeouts can't race.
 * Ringtone, caller-voice playback, audio routing and recording are separate objects owned here and are
 * always released in [finishSession].
 */
class CallSessionManager(
    private val app: Context,
    private val scope: CoroutineScope,
    private val calls: CallDao,
    private val recordings: RecordingRepository,
    private val settings: SettingsStore,
    private val notifications: NotificationHelper,
    private val leases: VoiceLeases,
) {
    private val gate = CompletableDeferred<Unit>()
    private val mutex = Mutex()
    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session.asStateFlow()

    private val ringtone = RingtonePlayer(app)
    private val voice = VoicePlayer(app, inCall = true)
    private val router = AudioRouter(app)
    private val recorder = CallRecorder(app)

    private var ringTimeoutJob: Job? = null
    private var ringingStarted = false

    /**
     * True while the phone's own notification sound is doing the ringing because the app's ringtone could not
     * (see [switchToSystemRing]). The foreground service reads it so it keeps posting the same kind of notification.
     */
    @Volatile
    var systemRing: Boolean = false
        private set
    private var heldClipWasPlaying = false
    private var heldRecordingWasRunning = false

    init {
        scope.launch { voice.state.collect { v -> _session.update { it.copy(voice = v) } } }
        scope.launch {
            recorder.state.collect { r ->
                _session.update { it.copy(recording = r) }
                if (r is RecorderState.Error) onRecorderFailed(r.message)
            }
        }
    }

    // ---- startup -----------------------------------------------------------------------------------------

    /** On a cold start nothing is really live, so any persisted RINGING/ACTIVE row is stale. */
    suspend fun reconcileStale() {
        val now = System.currentTimeMillis()
        for (e in calls.getLive()) {
            val status = if (e.status == CallStatus.ACTIVE.name) CallStatus.ENDED else CallStatus.MISSED
            calls.update(e.copy(status = status.name, endedAtMillis = now, endReason = "Interrupted when the app stopped"))
        }
        notifications.cancelAll()
    }

    fun markReady() { gate.complete(Unit) }
    suspend fun awaitReady() { gate.await() }

    // ---- alarm / ringing ---------------------------------------------------------------------------------

    suspend fun onAlarm(eventId: Long) {
        gate.await()
        mutex.withLock {
            val e = calls.get(eventId)
            if (e == null) {
                CallDiagnostics.log("Alarm ignored", "event $eventId no longer exists")
                return
            }
            if (e.status != CallStatus.SCHEDULED.name) {
                CallDiagnostics.log("Alarm ignored", "event $eventId is already ${e.status}")
                return
            }
            if (_session.value.isLive) {
                CallDiagnostics.log("Call missed", "event $eventId came while another call was in progress")
                closeEvent(e, CallStatus.MISSED, "Another call was in progress")
                notifications.postMissed(e.id, e.callerName, e.callerPhotoFile)
                return
            }
            try {
                startRinging(e)
            } catch (t: CancellationException) {
                throw t
            } catch (t: Throwable) {
                Log.e(TAG, "Couldn't start the call", t)
                CallDiagnostics.log("Call could not start", "${t.javaClass.simpleName}: ${t.message}")
                closeEvent(e, CallStatus.ERROR, "Couldn't start the call")
                ringtone.stop()
                ringingStarted = false
                systemRing = false
                _session.value = SessionState()
                notifications.cancelAll()
            }
        }
    }

    private suspend fun startRinging(e: CallEventEntity) {
        if (CallStateMachine.next(CallStatus.SCHEDULED, CallEvent.FIRE) != CallStatus.RINGING) return
        calls.update(e.copy(status = CallStatus.RINGING.name))
        try {
            settings.setLastCaller(e.callerId)
        } catch (c: CancellationException) {
            throw c
        } catch (t: Exception) {
            Log.w(TAG, "Could not remember the last caller", t)
        }
        ringingStarted = false
        systemRing = false
        _session.value = SessionState(
            eventId = e.id,
            callerId = e.callerId,
            status = CallStatus.RINGING,
            callerName = e.callerName,
            callerPhone = e.callerPhone,
            callerPhotoFile = e.callerPhotoFile,
            callerCountry = e.callerCountry,
            voiceUri = e.voiceUri,
            voiceLabel = e.voiceLabel,
        )
        heldClipWasPlaying = false
        heldRecordingWasRunning = false

        // Three independent things, so one of them failing never leaves the call silent and invisible:
        // 1) show the call (lock screen, heads-up, full screen) right now, without waiting for any service,
        val shown = notifications.postIncoming(_session.value)
        // 2) keep the app alive in the foreground while it rings,
        val serviceStarted = CallService.start(app)
        CallDiagnostics.log(
            "Call is ringing",
            "event=${e.id} late=${(System.currentTimeMillis() - e.triggerAtMillis) / 1000}s notification=${if (shown) "shown" else "NOT shown"} " +
                "service=${if (serviceStarted) "started" else "REFUSED"} ${systemState()}",
        )
        // 3) open the call screen over whatever is on screen, as a real call does (when Android allows it),
        CallScreenLauncher.show(app, e.id)
        // 4) ring and vibrate right now, in the way this phone rings for a real call.
        if (serviceStarted) startRingtoneLocked() else switchToSystemRing("Android refused to start the ringing service")
        val timeout = settings.current().ringTimeoutSeconds * 1000L
        ringTimeoutJob?.cancel()
        ringTimeoutJob = scope.launch {
            delay(timeout)
            ringExpired(e.id)
        }
    }

    /** Called by the service once it is in the foreground. Safe to call repeatedly. */
    fun beginRinging() {
        scope.launch { mutex.withLock { startRingtoneLocked() } }
    }

    private suspend fun startRingtoneLocked() {
        if (_session.value.status != CallStatus.RINGING || ringingStarted || systemRing) return
        if (dndRequiresSystemRing()) {
            // Do Not Disturb would mute the app's own ringtone and vibration, but it lets the call channel through.
            switchToSystemRing("Do Not Disturb is on, so the phone's own call alert rings instead")
            return
        }
        ringingStarted = true
        val st = settings.current()
        val result = withContext(Dispatchers.IO) {
            ringtone.start(st.ringtoneSource, st.ringtoneUri, st.vibrate, loop = true, followPhone = st.followPhoneRinger)
        }
        CallDiagnostics.log("Ringing", "${result.name.lowercase()} - ${ringtone.lastReport}")
        // Only a real failure falls back to the phone's own notification sound; silent / vibrate mode is not a failure.
        if (result == RingtonePlayer.Result.FAILED) switchToSystemRing("the ringtone could not be played")
    }

    /**
     * Last line of defence: let Android itself ring through the "backup" notification (default ringtone, repeating
     * until answered). The system plays it from its own process, so it still works when ours is restricted.
     */
    private fun switchToSystemRing(reason: String) {
        if (systemRing || _session.value.status != CallStatus.RINGING) return
        systemRing = true
        ringingStarted = true
        ringtone.stop()
        val shown = notifications.postBackupIncoming(_session.value)
        CallDiagnostics.log("Using the phone's own notification sound", "$reason; notification=${if (shown) "shown" else "NOT shown"}")
    }

    /** True when Do Not Disturb would mute the app's own ringer but the call channel is allowed through it. */
    private fun dndRequiresSystemRing(): Boolean =
        DoNotDisturb.shouldRingViaSystem(DoNotDisturb.read(app), notifications.incomingChannelOverridesDnd())

    fun onServiceFailed() {
        scope.launch {
            mutex.withLock {
                if (_session.value.status == CallStatus.RINGING) switchToSystemRing("Android refused to run the ringing service")
            }
        }
    }

    /** The call screen is on screen: make sure a ringing call really rings (the app is now allowed to start the service). */
    fun onCallScreenVisible() {
        scope.launch {
            mutex.withLock {
                if (_session.value.status == CallStatus.RINGING && !CallService.isRunning) {
                    if (systemRing && !dndRequiresSystemRing()) {
                        // The app is on screen now, so it may run its own ringer again instead of the system fallback.
                        // Cancelling the backup notification also stops its repeating sound.
                        systemRing = false
                        ringingStarted = false
                        notifications.cancelAll()
                        notifications.postIncoming(_session.value)
                    }
                    if (!CallService.start(app)) startRingtoneLocked()
                }
            }
        }
    }

    private suspend fun ringExpired(eventId: Long) {
        mutex.withLock {
            val s = _session.value
            if (s.eventId != eventId || s.status != CallStatus.RINGING) return
            ringTimeoutJob = null
            if (CallStateMachine.next(CallStatus.RINGING, CallEvent.RING_TIMEOUT) == CallStatus.MISSED) {
                finishSession(CallStatus.MISSED, "No answer")
            }
        }
    }

    // ---- answer / decline / end ---------------------------------------------------------------------------

    fun answer(eventId: Long) {
        stopRingingNow(eventId)
        scope.launch {
            gate.await()
            mutex.withLock { doAnswer(eventId) }
        }
    }

    private suspend fun doAnswer(eventId: Long) {
        val s = _session.value
        if (s.eventId != eventId || s.status != CallStatus.RINGING) return
        val next = CallStateMachine.next(CallStatus.RINGING, CallEvent.ANSWER) ?: return
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        ringtone.stop()
        ringingStarted = false
        systemRing = false
        notifications.cancelAll()
        val st = settings.current()
        // "Ongoing call timer" (Settings): the call looks as if it began this long ago, so the timer in the notification,
        // on every call screen and in the call history starts there and keeps running.
        val head = CallTimerOffset.toMillis(st.callTimerOffsetSeconds)
        val answeredAt = System.currentTimeMillis() - head
        calls.get(eventId)?.let { calls.update(it.copy(status = next.name, answeredAtMillis = answeredAt)) }
        router.enterCall()
        if (st.startOnSpeaker) router.setSpeaker(true)
        _session.update {
            it.copy(
                status = next,
                answeredAtMillis = answeredAt,
                answeredElapsed = SystemClock.elapsedRealtime() - head,
                speakerOn = router.isSpeakerOn(),
            )
        }
        if (!CallService.isRunning) CallService.start(app)
        val playable = withContext(Dispatchers.IO) { voice.load(s.voiceUri, repeat = st.voiceMode == VoicePlayMode.AUTO_REPEAT) }
        if (playable && st.voiceMode != VoicePlayMode.MANUAL) voice.play()
    }

    fun decline(eventId: Long) {
        stopRingingNow(eventId)
        scope.launch {
            gate.await()
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.RINGING) return@withLock
                if (CallStateMachine.next(CallStatus.RINGING, CallEvent.DECLINE) == CallStatus.REJECTED) {
                    finishSession(CallStatus.REJECTED, "Declined")
                }
            }
        }
    }

    fun end(eventId: Long) {
        scope.launch {
            gate.await()
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE) return@withLock
                if (CallStateMachine.next(CallStatus.ACTIVE, CallEvent.END) == CallStatus.ENDED) {
                    finishSession(CallStatus.ENDED, null)
                }
            }
        }
    }

    /** Immediate, lock-free silence for the ringtone; the authoritative state change follows under the lock. */
    private fun stopRingingNow(eventId: Long) {
        val s = _session.value
        if (s.eventId == eventId && s.status == CallStatus.RINGING) ringtone.stop()
    }

    private suspend fun finishSession(status: CallStatus, reason: String?) {
        val s = _session.value
        ringTimeoutJob?.cancel()
        ringTimeoutJob = null
        ringtone.stop()
        ringingStarted = false
        systemRing = false
        val rec = recorder.state.value
        if (rec.isActive || rec is RecorderState.Error) finalizeRecording(null)
        voice.release()
        router.exitCall()
        calls.get(s.eventId)?.let {
            calls.update(it.copy(status = status.name, endedAtMillis = System.currentTimeMillis(), endReason = reason))
        }
        leases.releaseIfUnused(s.voiceUri)
        heldClipWasPlaying = false
        heldRecordingWasRunning = false
        _session.value = SessionState()
        notifications.cancelAll()
        if (status == CallStatus.MISSED) notifications.postMissed(s.eventId, s.displayName, s.callerPhotoFile)
    }

    private suspend fun closeEvent(e: CallEventEntity, status: CallStatus, reason: String) {
        calls.update(e.copy(status = status.name, endedAtMillis = System.currentTimeMillis(), endReason = reason))
        leases.releaseIfUnused(e.voiceUri)
    }

    // ---- in-call controls --------------------------------------------------------------------------------

    fun toggleMute(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE) return@withLock
                val muted = !s.muted
                recorder.setMuted(muted)
                _session.update {
                    it.copy(
                        muted = muted,
                        message = if (muted && !it.recording.isActive) {
                            UiMessage("Mute silences your microphone in recordings. Nothing is being recorded right now.")
                        } else it.message,
                    )
                }
            }
        }
    }

    fun toggleSpeaker(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE) return@withLock
                val want = !s.speakerOn
                val ok = router.setSpeaker(want)
                val actual = router.isSpeakerOn()
                _session.update {
                    it.copy(
                        speakerOn = actual,
                        message = if (!ok || actual != want) UiMessage("This device couldn't switch the audio output.") else it.message,
                    )
                }
            }
        }
    }

    /** Hold pauses the caller clip and the recording, and resumes exactly what was running when you resume. */
    fun toggleHold(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE) return@withLock
                if (!s.held) {
                    heldClipWasPlaying = voice.state.value == VoiceState.Playing
                    if (heldClipWasPlaying) voice.pause()
                    heldRecordingWasRunning = recorder.state.value is RecorderState.Recording
                    if (heldRecordingWasRunning) recorder.pause()
                    _session.update { it.copy(held = true) }
                } else {
                    if (heldClipWasPlaying) voice.play()
                    if (heldRecordingWasRunning) recorder.resume()
                    heldClipWasPlaying = false
                    heldRecordingWasRunning = false
                    _session.update { it.copy(held = false) }
                }
            }
        }
    }

    fun voiceToggle(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE || s.held) return@withLock
                when (val v = voice.state.value) {
                    VoiceState.Playing -> voice.pause()
                    VoiceState.Paused, VoiceState.Ready -> voice.play()
                    VoiceState.Finished -> voice.replay()
                    is VoiceState.Error -> {
                        val st = settings.current()
                        val ok = withContext(Dispatchers.IO) {
                            voice.load(s.voiceUri, repeat = st.voiceMode == VoicePlayMode.AUTO_REPEAT)
                        }
                        if (ok) voice.play() else Log.w(TAG, "Voice clip still unavailable: ${v.message}")
                    }
                    VoiceState.NoClip -> Unit
                }
            }
        }
    }

    // ---- recording ---------------------------------------------------------------------------------------

    /** User tapped Record in the visible call screen. Microphone access is checked here, at the point of use. */
    fun startRecording(eventId: Long) {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.eventId != eventId || s.status != CallStatus.ACTIVE || s.recording.isActive) return@withLock
                if (!SystemAccess.micGranted(app)) {
                    setMessage(UiMessage("Microphone access is needed to record.", MessageAction.OPEN_APP_SETTINGS))
                    return@withLock
                }
                if (!CallService.start(app, CallService.ACTION_RECORD)) {
                    setMessage(UiMessage("Recording can't run in the background on this device right now. Try again."))
                }
            }
        }
    }

    /** The service has promoted itself with the microphone type; now it is legal to capture. */
    fun beginRecording() {
        scope.launch {
            mutex.withLock {
                val s = _session.value
                if (s.status != CallStatus.ACTIVE || s.recording.isActive) return@withLock
                val st = settings.current()
                val error = withContext(Dispatchers.IO) { recorder.start(st.recordingSampleRate, s.muted) }
                if (error != null) {
                    setMessage(UiMessage(error))
                    CallService.start(app)
                }
            }
        }
    }

    fun recordingUnavailable() {
        setMessage(UiMessage("Recording couldn't start. Check microphone access and try again."))
    }

    fun micDenied() {
        setMessage(UiMessage("Microphone access was denied. You can allow it in the app's settings.", MessageAction.OPEN_APP_SETTINGS))
    }

    fun pauseRecording() { if (!_session.value.held) recorder.pause() }
    fun resumeRecording() { if (!_session.value.held) recorder.resume() }

    fun stopRecording() {
        scope.launch {
            mutex.withLock {
                if (recorder.state.value.isActive) {
                    finalizeRecording(null)
                    CallService.start(app)
                }
            }
        }
    }

    private fun onRecorderFailed(message: String) {
        scope.launch {
            mutex.withLock {
                if (recorder.state.value is RecorderState.Error) {
                    finalizeRecording("$message What was captured so far has been saved.")
                    CallService.start(app)
                }
            }
        }
    }

    private suspend fun finalizeRecording(note: String?) {
        val outcome = withContext(Dispatchers.IO) { recorder.stop() } ?: return
        val s = _session.value
        if (outcome.durationMs > 0) {
            recordings.add(outcome.file, outcome.durationMs, outcome.sampleRate, s.callerId, s.displayName)
            setMessage(UiMessage(note ?: "Recording saved to your library."))
        } else {
            withContext(Dispatchers.IO) { outcome.file.delete() }
            setMessage(UiMessage(note ?: "Nothing was recorded."))
        }
    }

    // ---- messages ----------------------------------------------------------------------------------------

    private fun setMessage(message: UiMessage) { _session.update { it.copy(message = message) } }

    /**
     * One line about the phone's call-related settings, for the call report in Settings.
     * It is only a report: whatever goes wrong while reading these must never get in the way of the call itself.
     */
    private fun systemState(): String = try {
        "notifications=${if (SystemAccess.notificationsEnabled(app)) "on" else "OFF"} " +
            "alerts=${notifications.incomingChannelState()} " +
            "dnd=${DoNotDisturb.describe(DoNotDisturb.read(app))} " +
            "overridesDnd=${if (notifications.incomingChannelOverridesDnd()) "yes" else "no"} " +
            "overOtherApps=${if (SystemAccess.canDrawOverOtherApps(app)) "yes" else "no"} " +
            "fullScreen=${if (SystemAccess.fullScreenAllowed(app)) "yes" else "NO"} " +
            "exactAlarms=${if (SystemAccess.exactAlarmsAllowed(app)) "yes" else "NO"} " +
            "batteryUnrestricted=${if (SystemAccess.ignoringBatteryOptimizations(app)) "yes" else "no"} " +
            "android=${android.os.Build.VERSION.SDK_INT}"
    } catch (e: Exception) {
        "(settings unreadable: ${e.javaClass.simpleName})"
    }

    fun clearMessage() { _session.update { it.copy(message = null) } }

    private companion object {
        const val TAG = "CallSession"
    }
}
