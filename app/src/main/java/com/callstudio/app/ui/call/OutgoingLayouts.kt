package com.callstudio.app.ui.call

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.fakeoutgoing.FakePhase
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.delay

data class OutgoingCallModel(
    val caller: CallerUi,
    val phase: FakePhase,
    val elapsedMs: Long,
    val endedLabel: String?,
    val muted: Boolean,
    val speakerOn: Boolean,
    val held: Boolean,
    val onKeypad: () -> Unit,
    val onMute: () -> Unit,
    val onSpeaker: () -> Unit,
    val onHold: () -> Unit,
    val onEnd: () -> Unit,
)

/** The Truecaller-style theme gets its own outgoing screen; every other theme uses the stock-phone look. */
@Composable
fun OutgoingCallScreen(theme: CallTheme, model: OutgoingCallModel) {
    if (theme == CallTheme.TRUECALLER) TruecallerOutgoing(model) else StockOutgoing(model)
}

/** "Calling…", "Ringing…", the running timer, or why the call ended. */
@Composable
fun outgoingStatusText(m: OutgoingCallModel): String {
    val dots by produceState(initialValue = 1, m.phase) {
        while (true) {
            delay(450)
            value = (value % 3) + 1
        }
    }
    return when (m.phase) {
        FakePhase.DIALING -> "Calling" + ".".repeat(dots)
        FakePhase.RINGING -> "Ringing" + ".".repeat(dots)
        FakePhase.CONNECTED -> if (m.held) "On hold" else TimeFormat.elapsed(m.elapsedMs)
        FakePhase.ENDED -> m.endedLabel ?: "Call ended"
        FakePhase.IDLE -> ""
    }
}

private val StockFont = FontFamily.SansSerif

@Composable
private fun StockOutgoing(m: OutgoingCallModel) {
    val status = outgoingStatusText(m)
    val calling = m.phase == FakePhase.DIALING || m.phase == FakePhase.RINGING
    CallSurface(SolidColor(Color(0xFF1E2025))) {
        val avatar = minOf(maxWidth * 0.36f, maxHeight * 0.18f).coerceAtLeast(88.dp)
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(44.dp))
                if (calling) {
                    Row {
                        Text("Calling via ", color = Color(0xFFC4C7C5), fontSize = 20.sp, fontFamily = StockFont)
                        Text("SIM 1", color = Color(0xFF4FD8E8), fontSize = 20.sp, fontFamily = StockFont)
                    }
                } else {
                    Spacer(Modifier.height(26.dp))
                }
                Spacer(Modifier.height(22.dp))
                Text(
                    m.caller.title, color = Color(0xFFE3E3E3), fontSize = 40.sp, fontFamily = StockFont,
                    fontWeight = FontWeight.Light, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (m.caller.name.isNotBlank() && m.caller.phone.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(m.caller.phone, color = Color(0xFFC4C7C5), fontSize = 18.sp, fontFamily = StockFont)
                }
                if (!m.caller.country.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(m.caller.country, color = Color(0xFFC4C7C5), fontSize = 18.sp, fontFamily = StockFont)
                }
                Spacer(Modifier.height(10.dp))
                AnimatedContent(
                    targetState = status,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
                    label = "status",
                ) { text ->
                    Text(text, color = Color(0xFFC4C7C5), fontSize = 21.sp, fontFamily = StockFont)
                }
                Spacer(Modifier.height(40.dp))
                if (calling) {
                    PulseRing(color = Color(0xFF4FD8E8), size = avatar) {
                        CallerAvatar(m.caller.photoFile, m.caller.title, avatar, background = Color(0xFF3A4046), foreground = Color(0xFFE3E3E3))
                    }
                } else {
                    CallerAvatar(m.caller.photoFile, m.caller.title, avatar, background = Color(0xFF3A4046), foreground = Color(0xFFE3E3E3))
                }
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color(0xFF26282D))
                    .padding(horizontal = 12.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StockButton("Keypad", Icons.Filled.Dialpad, false, m.onKeypad)
                    StockButton("Mute", if (m.muted) Icons.Filled.MicOff else Icons.Filled.Mic, m.muted, m.onMute)
                    StockButton("Speaker", Icons.Filled.VolumeUp, m.speakerOn, m.onSpeaker)
                    StockButton(
                        if (m.held) "Resume" else "Hold",
                        if (m.held) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        m.held, m.onHold, enabled = m.phase == FakePhase.CONNECTED,
                    )
                }
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier.width(156.dp).height(66.dp).clip(RoundedCornerShape(33.dp)).background(Color(0xFFEF675C))
                        .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color(0xFF1B1B1B), modifier = Modifier.size(30.dp)) }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StockButton(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.width(76.dp).height(64.dp).clip(RoundedCornerShape(32.dp))
                .background(if (active) Color(0xFFD3E3FD) else Color(0xFF111418))
                .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon, contentDescription = null,
                tint = if (!enabled) Color(0xFF6E7378) else if (active) Color(0xFF041E49) else Color(0xFFE3E3E3),
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = if (enabled) Color(0xFFE3E3E3) else Color(0xFF6E7378), fontSize = 15.sp, fontFamily = StockFont, maxLines = 1)
    }
}
