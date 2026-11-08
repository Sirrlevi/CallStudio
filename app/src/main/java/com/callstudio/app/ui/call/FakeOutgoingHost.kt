package com.callstudio.app.ui.call

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callstudio.app.fakeoutgoing.FakeOutgoingState
import com.callstudio.app.fakeoutgoing.FakePhase
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.delay

class FakeOutgoingActions(
    val onMute: () -> Unit,
    val onSpeaker: () -> Unit,
    val onHold: () -> Unit,
    val onEnd: () -> Unit,
    val onDismissNotice: () -> Unit,
)

/** Draws the staged outgoing call in the chosen theme and shows the keypad and any notice. */
@Composable
fun FakeOutgoingHost(state: FakeOutgoingState, theme: CallTheme, actions: FakeOutgoingActions) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.phase) {
        if (state.phase == FakePhase.CONNECTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    val model = OutgoingCallModel(
        caller = CallerUi(state.name, state.number, state.photoFile, state.country),
        phase = state.phase,
        elapsedMs = (now - state.answeredElapsed).coerceAtLeast(0L),
        endedLabel = state.endedLabel,
        muted = state.muted,
        speakerOn = state.speakerOn,
        held = state.held,
        onKeypad = { showKeypad = true },
        onMute = actions.onMute,
        onSpeaker = actions.onSpeaker,
        onHold = actions.onHold,
        onEnd = actions.onEnd,
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        OutgoingCallScreen(theme, model)
        state.notice?.let { text ->
            Box(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).padding(bottom = 96.dp),
            ) {
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, color = Color(0xF2202124)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text, color = Color.White, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        TextButton(onClick = actions.onDismissNotice) { Text("OK") }
                    }
                }
            }
        }
    }
    if (showKeypad && state.phase != FakePhase.IDLE && state.phase != FakePhase.ENDED) KeypadDialog { showKeypad = false }
}
