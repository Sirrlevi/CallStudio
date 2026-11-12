package com.callstudio.app.ui.call

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.callstudio.app.domain.CallStatus
import com.callstudio.app.preferences.EdgeGlowStyle
import com.callstudio.app.session.MessageAction
import com.callstudio.app.session.SessionState
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.delay

/** Shows the ringing or in-call screen for the live session in the selected theme, plus transient messages. */
@Composable
fun CallHost(
    session: SessionState,
    theme: CallTheme,
    actions: CallActions,
    onMessageAction: (MessageAction) -> Unit,
    onDismissMessage: () -> Unit,
    edgeGlow: EdgeGlowStyle = EdgeGlowStyle.OFF,
) {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(session.status) {
        if (session.status == CallStatus.ACTIVE) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(500)
            }
        }
    }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    val caller = CallerUi(session.callerName, session.callerPhone, session.callerPhotoFile, session.callerCountry)
    val extras = listOf(
        ControlSpec("keypad", "Keypad", Icons.Filled.Dialpad, onClick = { showKeypad = true }),
        ControlSpec(
            "hold", if (session.held) "Resume" else "Hold",
            if (session.held) Icons.Filled.PlayArrow else Icons.Filled.Pause,
            active = session.held, onClick = actions.onHold,
        ),
        ControlSpec("message", "Message", Icons.Filled.Sms, onClick = actions.onMessage),
    )
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AnimatedContent(
            targetState = session.status,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
            label = "callPhase",
            modifier = Modifier.fillMaxSize(),
        ) { status ->
            when (status) {
                CallStatus.RINGING -> IncomingCallScreen(theme, caller, IncomingActions(actions.onAnswer, actions.onDecline, actions.onMessage))
                CallStatus.ACTIVE -> InCallScreen(
                    theme,
                    InCallModel(
                        caller = caller,
                        elapsedMs = (now - session.answeredElapsed).coerceAtLeast(0L),
                        recordingLabel = recordingLabel(session.recording),
                        controls = buildControls(session, actions),
                        onEnd = actions.onEnd,
                        extraControls = extras,
                        held = session.held,
                    ),
                )
                else -> Box(Modifier.fillMaxSize().background(Color.Black))
            }
        }
        // Edge lighting only while ringing, drawn above the call screen and never intercepting touches.
        if (session.status == CallStatus.RINGING) EdgeGlow(edgeGlow)
        if (showKeypad && session.status == CallStatus.ACTIVE) KeypadDialog { showKeypad = false }
        val message = session.message
        if (message != null) {
            Box(
                Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing).padding(16.dp).padding(bottom = 96.dp),
            ) {
                Surface(shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, color = Color(0xF2202124)) {
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(message.text, color = Color.White, modifier = Modifier.weight(1f).padding(vertical = 12.dp))
                        if (message.action == MessageAction.OPEN_APP_SETTINGS) {
                            TextButton(onClick = { onMessageAction(message.action); onDismissMessage() }) { Text("Settings") }
                        }
                        TextButton(onClick = onDismissMessage) { Text("OK") }
                    }
                }
            }
        }
    }
}
