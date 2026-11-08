package com.callstudio.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.fakeout.FakePhase
import com.callstudio.app.ui.call.FakeOutgoingActions
import com.callstudio.app.ui.call.FakeOutgoingCallScreen
import kotlinx.coroutines.delay

/**
 * The fake outgoing call screen. It only draws the state owned by [com.callstudio.app.fakeout.FakeOutgoingEngine],
 * so rotating, leaving and coming back never start or end a call by themselves.
 */
class FakeOutgoingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        // Volume keys change the call volume, like in a real call.
        volumeControlStream = AudioManager.STREAM_VOICE_CALL
        val engine = container.fakeOutgoing

        setContent {
            val state by engine.state.collectAsStateWithLifecycle()

            // Close once the call is over; give a brand-new launch a moment for the engine state to arrive.
            val seenLive = remember { booleanArrayOf(false) }
            LaunchedEffect(state.phase) {
                if (state.phase != FakePhase.IDLE) {
                    seenLive[0] = true
                } else {
                    if (!seenLive[0]) delay(600)
                    if (engine.state.value.phase == FakePhase.IDLE) finish()
                }
            }
            LaunchedEffect(state.inCall) {
                if (state.inCall) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            // Like a real call screen, Back doesn't hang up by accident.
            BackHandler(enabled = state.isLive) { }

            val actions = remember {
                FakeOutgoingActions(
                    onEnd = { engine.end() },
                    onMute = { engine.toggleMute() },
                    onSpeaker = { engine.toggleSpeaker() },
                    onHold = { engine.toggleHold() },
                    onMessage = { openMessaging(engine.state.value.request?.number.orEmpty()) },
                )
            }
            FakeOutgoingCallScreen(state, actions)
        }
    }

    private fun openMessaging(shownNumber: String) {
        val number = PhoneNumbers.dialable(shownNumber)
        if (number.isBlank()) return
        try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:$number")))
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
        fun intent(context: Context): Intent =
            Intent(context, FakeOutgoingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
