package com.callstudio.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.notifications.NotificationHelper
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.session.MessageAction
import com.callstudio.app.ui.call.CallActions
import com.callstudio.app.ui.call.CallHost
import com.callstudio.app.ui.viewmodels.CallViewModel
import kotlinx.coroutines.delay

/**
 * The ringing / in-call screen. It only renders the session owned by CallSessionManager, so rotation,
 * recreation, backgrounding and reopening from the notification never create or end a call by themselves.
 */
class CallActivity : ComponentActivity() {
    private val viewModel: CallViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge()
        handleIntent(intent)
        setContent {
            val session by viewModel.session.collectAsStateWithLifecycle()
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val context = LocalContext.current
            val s = settings

            val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
                if (granted) viewModel.startRecording() else viewModel.micDenied()
            }

            // Leave as soon as a call that was live has ended; give a brand-new launch a moment to catch up.
            var seenLive = remember { booleanArrayOf(false) }
            LaunchedEffect(session.isLive) {
                if (session.isLive) {
                    seenLive[0] = true
                } else {
                    if (!seenLive[0]) delay(600)
                    if (!viewModel.session.value.isLive) finish()
                }
            }
            LaunchedEffect(session.status, s?.keepScreenOn) {
                if (session.isLive && s?.keepScreenOn != false) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }

            if (s != null) {
                val actions = remember(viewModel) {
                    CallActions(
                        onAnswer = { viewModel.answer() },
                        onDecline = { viewModel.decline() },
                        onMute = { viewModel.toggleMute() },
                        onSpeaker = { viewModel.toggleSpeaker() },
                        onRecord = {
                            when {
                                viewModel.session.value.recording.isActive -> viewModel.stopRecording()
                                SystemAccess.micGranted(context) -> viewModel.startRecording()
                                else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        onRecordPause = { viewModel.toggleRecordingPause() },
                        onVoice = { viewModel.voiceToggle() },
                        onEnd = { viewModel.end() },
                        onHold = { viewModel.toggleHold() },
                        onMessage = {
                            val current = viewModel.session.value
                            if (current.status == CallStatus.RINGING) viewModel.decline()
                            openMessaging(context, current.callerPhone)
                        },
                    )
                }
                CallHost(
                    session = session,
                    theme = s.theme,
                    actions = actions,
                    onMessageAction = { if (it == MessageAction.OPEN_APP_SETTINGS) SystemAccess.openAppSettings(context) },
                    onDismissMessage = { viewModel.clearMessage() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        val eventId = intent.getLongExtra(NotificationHelper.EXTRA_EVENT_ID, -1L)
        viewModel.onScreenVisible()
        // Answer is idempotent, so re-delivering this intent after a rotation can't start a second session.
        if (intent.getBooleanExtra(EXTRA_ANSWER, false) && eventId >= 0) viewModel.answerEvent(eventId)
    }

    /** Opens the messaging app addressed to the caller; does nothing when the caller has no number. */
    private fun openMessaging(context: Context, displayNumber: String) {
        val number = PhoneNumbers.dialable(displayNumber)
        if (number.isBlank()) return
        try {
            context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            // No messaging app available.
        }
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
    }

    companion object {
        const val EXTRA_ANSWER = "answer_on_open"
        private const val ACTION_OPEN = "com.callstudio.app.action.OPEN_CALL"
        private const val ACTION_ANSWER = "com.callstudio.app.action.ANSWER_CALL"

        /** Unique per event and purpose, so one call's notification can never open another caller's screen. */
        fun intent(context: Context, eventId: Long, answer: Boolean = false): Intent =
            Intent(context, CallActivity::class.java)
                .setAction(if (answer) ACTION_ANSWER else ACTION_OPEN)
                .setData(Uri.parse("callstudio://call/$eventId/${if (answer) "answer" else "open"}"))
                .putExtra(NotificationHelper.EXTRA_EVENT_ID, eventId)
                .putExtra(EXTRA_ANSWER, answer)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
