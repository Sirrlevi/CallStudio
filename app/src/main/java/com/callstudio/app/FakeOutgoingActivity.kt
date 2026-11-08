package com.callstudio.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callstudio.app.fakeoutgoing.FakePhase
import com.callstudio.app.preferences.AppSettings
import com.callstudio.app.ui.call.FakeOutgoingActions
import com.callstudio.app.ui.call.FakeOutgoingHost
import kotlinx.coroutines.delay

/** The staged outgoing call screen. The call itself lives in FakeOutgoingController, so rotating or leaving is safe. */
class FakeOutgoingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val controller = container.fakeOutgoing
        val settingsFlow = container.settings.settings

        setContent {
            val state by controller.state.collectAsStateWithLifecycle()
            val settings by settingsFlow.collectAsStateWithLifecycle(initialValue = null as AppSettings?)
            var seenActive by remember { mutableStateOf(false) }

            // Close when the call is over, as a phone returns to where you were.
            LaunchedEffect(state.phase) {
                if (state.phase != FakePhase.IDLE) {
                    seenActive = true
                } else {
                    if (!seenActive) delay(600)
                    if (controller.state.value.phase == FakePhase.IDLE) finish()
                }
            }
            // Back keeps the call going, like a real call.
            BackHandler { moveTaskToBack(true) }

            val s = settings
            if (s != null && state.phase != FakePhase.IDLE) {
                FakeOutgoingHost(
                    state = state,
                    theme = s.theme,
                    actions = FakeOutgoingActions(
                        onMute = { controller.toggleMute() },
                        onSpeaker = { controller.toggleSpeaker() },
                        onHold = { controller.toggleHold() },
                        onEnd = { controller.end() },
                        onDismissNotice = { controller.clearNotice() },
                    ),
                )
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent =
            Intent(context, FakeOutgoingActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
