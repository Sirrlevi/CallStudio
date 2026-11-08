package com.callstudio.app.ui.call

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callstudio.app.fakeout.FakeCallState
import com.callstudio.app.fakeout.FakePhase
import com.callstudio.app.ui.components.CallerAvatar
import kotlinx.coroutines.delay
import java.util.Locale

class FakeOutgoingActions(
    val onEnd: () -> Unit,
    val onMute: () -> Unit,
    val onSpeaker: () -> Unit,
    val onHold: () -> Unit,
    val onMessage: () -> Unit,
)

// Colours and sizes measured from a real stock-Android dark outgoing call screen.
private val ScreenBg = Color(0xFF1C1F24)
private val PanelBg = Color(0xFF26292E)
private val ButtonBg = Color(0xFF101417)
private val TextMain = Color(0xFFE0E3E8)
private val TextSub = Color(0xFFC0C7CF)
private val SimCyan = Color(0xFF4DD0E4)
private val EndRed = Color(0xFFED675C)
private val EndIcon = Color(0xFF131313)
private val ActiveBg = Color(0xFFA8C7FA)
private val ActiveFg = Color(0xFF062E6F)

private val avatarPalette = listOf(
    Color(0xFFF28B82), Color(0xFFFDD663), Color(0xFF81C995), Color(0xFF78D9EC),
    Color(0xFFA8C7FA), Color(0xFFD7AEFB), Color(0xFFFF8BCB), Color(0xFFFCAD70),
)

private fun avatarColor(seed: String): Color = avatarPalette[(seed.hashCode() and Int.MAX_VALUE) % avatarPalette.size]

/** "00:07", or "1:02:03" past an hour; the same look as the stock call timer. */
fun callTimer(ms: Long): String {
    val total = ms.coerceAtLeast(0L) / 1000L
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s) else String.format(Locale.ROOT, "%02d:%02d", m, s)
}

/** The fake outgoing call screen: stock-Android dark style, driven entirely by [state]. */
@Composable
fun FakeOutgoingCallScreen(state: FakeCallState, actions: FakeOutgoingActions) {
    val req = state.request
    if (req == null) {
        Box(Modifier.fillMaxSize().background(ScreenBg))
        return
    }

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.phase) {
        if (state.phase == FakePhase.CONNECTED) {
            while (true) {
                now = SystemClock.elapsedRealtime()
                delay(250L)
            }
        }
    }
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var panelIn by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { panelIn = true }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Box(Modifier.fillMaxSize().background(ScreenBg)) {
            Column(Modifier.fillMaxSize()) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().statusBarsPadding().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Spacer(Modifier.height(48.dp))
                    StatusLine(state, req.settings.simSlot, callTimer(now - state.connectedElapsed))
                    Spacer(Modifier.height(30.dp))
                    if (req.name.isBlank()) {
                        Text(
                            req.number, color = TextMain, fontSize = 38.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        req.countryName?.let {
                            Spacer(Modifier.height(6.dp))
                            Text(it, color = TextSub, fontSize = 16.sp)
                        }
                    } else {
                        Text(
                            req.name, color = TextMain, fontSize = 38.sp, textAlign = TextAlign.Center,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(req.number, color = TextSub, fontSize = 16.sp)
                    }
                    if (req.name.isNotBlank()) {
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            val avatar: @Composable () -> Unit = {
                                CallerAvatar(
                                    req.photoFile, req.name, 132.dp,
                                    background = avatarColor(req.name), foreground = ScreenBg,
                                )
                            }
                            if (state.phase == FakePhase.CONNECTING || state.phase == FakePhase.RINGING) {
                                PulseRing(color = Color(0x40FFFFFF), size = 132.dp, content = avatar)
                            } else {
                                avatar()
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = panelIn,
                    enter = slideInVertically(tween(380)) { it / 2 } + fadeIn(tween(380)),
                ) {
                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                            .background(PanelBg)
                            .navigationBarsPadding()
                            .padding(top = 17.dp, bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 25.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            ControlButton(Icons.Filled.Dialpad, "Keypad", active = false, enabled = state.inCall) { showKeypad = true }
                            ControlButton(
                                if (state.muted) Icons.Filled.MicOff else Icons.Filled.Mic,
                                "Mute", active = state.muted, enabled = state.inCall, onClick = actions.onMute,
                            )
                            ControlButton(Icons.Filled.VolumeUp, "Speaker", active = state.speakerOn, enabled = state.inCall, onClick = actions.onSpeaker)
                            Box {
                                ControlButton(Icons.Filled.MoreVert, "More", active = showMore, enabled = state.inCall) { showMore = true }
                                DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (state.held) "Resume call" else "Hold call") },
                                        enabled = state.phase == FakePhase.CONNECTED,
                                        onClick = { showMore = false; actions.onHold() },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Message") },
                                        onClick = { showMore = false; actions.onMessage() },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(22.dp))
                        Box(
                            Modifier.size(width = 175.dp, height = 74.dp)
                                .alpha(if (state.inCall) 1f else 0.45f)
                                .clip(RoundedCornerShape(37.dp))
                                .background(EndRed)
                                .clickable(enabled = state.inCall, onClickLabel = "End call", role = Role.Button, onClick = actions.onEnd),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = EndIcon, modifier = Modifier.size(32.dp))
                        }
                    }
                }
            }
            if (showKeypad && state.inCall) KeypadDialog { showKeypad = false }
        }
    }
}

/** "Calling via SIM 1" -> "Ringing…" -> timer -> "Call ended", cross-fading between phases. */
@Composable
private fun StatusLine(state: FakeCallState, simSlot: Int, timer: String) {
    AnimatedContent(
        targetState = state.phase,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) },
        label = "fakeStatus",
    ) { phase ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            when (phase) {
                FakePhase.CONNECTING, FakePhase.IDLE -> {
                    if (simSlot > 0) {
                        Text(
                            buildAnnotatedString {
                                append("Calling via ")
                                withStyle(SpanStyle(color = SimCyan)) { append("SIM $simSlot") }
                            },
                            color = TextSub, fontSize = 16.sp,
                        )
                    } else {
                        Text("Calling…", color = TextSub, fontSize = 16.sp)
                    }
                }
                FakePhase.RINGING -> Text("Ringing…", color = TextSub, fontSize = 16.sp)
                FakePhase.CONNECTED -> Text(
                    if (state.held) "On hold" else timer,
                    color = TextSub, fontSize = 16.sp, fontWeight = FontWeight.Normal,
                )
                FakePhase.ENDED -> {
                    Text("Call ended", color = TextSub, fontSize = 16.sp)
                    state.endNote?.let {
                        Spacer(Modifier.height(4.dp))
                        Text(it, color = TextSub.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ControlButton(icon: ImageVector, label: String, active: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.width(83.dp).alpha(if (enabled) 1f else 0.45f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(width = 83.dp, height = 70.dp)
                .clip(RoundedCornerShape(35.dp))
                .background(if (active) ActiveBg else ButtonBg)
                .clickable(enabled = enabled, onClickLabel = label, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = if (active) ActiveFg else TextMain, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(label, color = TextSub, fontSize = 13.sp, maxLines = 1)
    }
}
