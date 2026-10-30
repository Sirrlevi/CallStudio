@file:OptIn(ExperimentalLayoutApi::class)

package com.callstudio.app.ui.call

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.asState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.ui.components.CallerAvatar
import kotlinx.coroutines.delay

private val TcGradient = Brush.verticalGradient(
    0f to Color(0xFF1570F5),
    0.38f to Color(0xFF1361DD),
    0.62f to Color(0xFF0B3A8C),
    0.82f to Color(0xFF051B47),
    1f to Color.Black,
)
private val TcRed = Color(0xFFFF4132)
private val TcGreen = Color(0xFF30B941)
private val TcFont = FontFamily.SansSerif

// ---- shared pieces ----------------------------------------------------------------------------------------

/** 0 -> 1 after [delayMs]; used to bring the screen in piece by piece. */
@Composable
private fun rememberEntrance(delayMs: Int): State<Float> {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMs.toLong())
        progress.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
    }
    return progress.asState()
}

private fun Modifier.entrance(progress: State<Float>): Modifier = graphicsLayer {
    alpha = progress.value
    translationY = (1f - progress.value) * 28.dp.toPx()
}

@Composable
private fun TcTitle(modifier: Modifier = Modifier) {
    Text("Caller ID", modifier, color = Color.White, fontSize = 28.sp, fontFamily = TcFont, fontWeight = FontWeight.Bold)
}

@Composable
private fun TcName(name: String, modifier: Modifier = Modifier) {
    Text(
        name, modifier, color = Color(0xFFF2F6FF), fontSize = 38.sp, fontFamily = TcFont, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 44.sp,
    )
}

@Composable
private fun ViewProfileButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x44FFFFFF), Color.Transparent)))
        )
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(Color.White)
                .clickable(onClickLabel = "View profile", role = Role.Button, onClick = onClick)
                .padding(horizontal = 28.dp, vertical = 14.dp),
        ) {
            Text("View profile", color = Color(0xFF111418), fontSize = 17.sp, fontFamily = TcFont, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ProfileDialog(c: CallerUi, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(c.title) },
        text = {
            Column {
                Text("Mobile")
                if (c.phone.isNotBlank()) Text(c.phone)
                if (!c.country.isNullOrBlank()) Text(c.country)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun RippleAvatar(c: CallerUi, size: Dp, rippling: Boolean = true) {
    val transition = rememberInfiniteTransition(label = "ripple")
    val first = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing)), label = "ring1",
    )
    val second = transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), initialStartOffset = StartOffset(1300)), label = "ring2",
    )
    Box(Modifier.size(size * 1.4f), contentAlignment = Alignment.Center) {
        if (rippling) {
            listOf(first, second).forEach { progress ->
                Box(
                    Modifier.size(size)
                        .graphicsLayer {
                            val s = 1f + 0.8f * progress.value
                            scaleX = s
                            scaleY = s
                            alpha = 0.35f * (1f - progress.value)
                        }
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }
        CallerAvatar(c.photoFile, c.title, size, background = Color(0xFF3F86FF), foreground = Color.White)
    }
}

// ---- incoming ---------------------------------------------------------------------------------------------

@Composable
fun TruecallerIncoming(c: CallerUi, a: IncomingActions) {
    var pressed by remember { mutableIntStateOf(0) } // 0 none, 1 decline, 2 answer
    var showProfile by remember { mutableStateOf(false) }
    val titleIn = rememberEntrance(0)
    val lineIn = rememberEntrance(90)
    val avatarIn = rememberEntrance(180)
    val nameIn = rememberEntrance(280)
    val profileIn = rememberEntrance(380)
    val buttonsIn = rememberEntrance(480)
    val pillAlpha by animateFloatAsState(if (pressed != 0) 0.45f else 1f, tween(250), label = "pillAlpha")
    val callLine = buildString {
        append("Call from ")
        append(c.phone.ifBlank { "unknown number" })
        if (!c.country.isNullOrBlank()) append(" · ").append(c.country)
    }

    CallSurface(TcGradient) {
        val avatar = minOf(maxWidth * 0.34f, maxHeight * 0.16f).coerceAtLeast(84.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))
                TcTitle(Modifier.entrance(titleIn))
                Spacer(Modifier.height(8.dp))
                Text(callLine, Modifier.entrance(lineIn), color = Color(0xE6FFFFFF), fontSize = 17.sp, fontFamily = TcFont, textAlign = TextAlign.Center)
                Spacer(Modifier.height(32.dp))
                Box(Modifier.entrance(avatarIn)) { RippleAvatar(c, avatar) }
                Spacer(Modifier.height(12.dp))
                TcName(c.title, Modifier.entrance(nameIn))
                Spacer(Modifier.height(24.dp))
                ViewProfileButton({ showProfile = true }, Modifier.entrance(profileIn))
            }
            Row(
                Modifier.fillMaxWidth().zIndex(if (pressed != 0) 2f else 0f).entrance(buttonsIn),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                GrowButton(
                    color = TcRed, icon = Icons.Filled.CallEnd, description = "Decline",
                    pressed = pressed == 1, dimmed = pressed == 2,
                    onPressChange = { down -> pressed = if (down) 1 else 0 },
                    onTrigger = a.onDecline,
                )
                GrowButton(
                    color = TcGreen, icon = Icons.Filled.Call, description = "Answer",
                    pressed = pressed == 2, dimmed = pressed == 1,
                    onPressChange = { down -> pressed = if (down) 2 else 0 },
                    onTrigger = a.onAnswer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(
                Modifier.alpha(pillAlpha).clip(RoundedCornerShape(50)).background(Color(0xFF2C2C2E))
                    .clickable(onClickLabel = "Decline and send a message", role = Role.Button, onClick = a.onMessage)
                    .padding(horizontal = 36.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Message", color = Color.White, fontSize = 19.sp, fontFamily = TcFont)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (showProfile) ProfileDialog(c) { showProfile = false }
}

/**
 * Press and hold: the button swells to fill the lower half while the other one fades; release to confirm, or slide
 * your finger off the button to cancel. A plain tap works too.
 */
@Composable
private fun GrowButton(
    color: Color,
    icon: ImageVector,
    description: String,
    pressed: Boolean,
    dimmed: Boolean,
    onPressChange: (Boolean) -> Unit,
    onTrigger: () -> Unit,
) {
    val scale by animateFloatAsState(
        if (pressed) 3.4f else 1f,
        spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "grow",
    )
    val dim by animateFloatAsState(if (dimmed) 0.3f else 1f, tween(250), label = "dim")
    val ringVisible by animateFloatAsState(if (pressed) 0f else 1f, tween(150), label = "ringVisible")
    val breathe = rememberInfiniteTransition(label = "breathe")
    val ring = breathe.animateFloat(
        1f, 1.14f, infiniteRepeatable(tween(1300, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "ring",
    )
    val haptic = LocalHapticFeedback.current
    val currentPress by rememberUpdatedState(onPressChange)
    val currentTrigger by rememberUpdatedState(onTrigger)

    Box(
        Modifier.size(100.dp).zIndex(if (pressed) 2f else 0f).graphicsLayer { alpha = dim }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentPress(true)
                    val up = waitForUpOrCancellation()
                    currentPress(false)
                    if (up != null) currentTrigger()
                }
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                onClick(label = description) {
                    currentTrigger()
                    true
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(88.dp)
                .graphicsLayer {
                    scaleX = ring.value
                    scaleY = ring.value
                    alpha = ringVisible
                }
                .clip(CircleShape).background(Color(0x55607D9E))
        )
        Box(
            Modifier.size(72.dp).graphicsLayer { scaleX = scale; scaleY = scale }.clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
        }
    }
}

// ---- in call ----------------------------------------------------------------------------------------------

@Composable
fun TruecallerInCall(m: InCallModel) {
    var showProfile by remember { mutableStateOf(false) }
    val titleIn = rememberEntrance(0)
    val bodyIn = rememberEntrance(120)
    val gridIn = rememberEntrance(260)
    val all = m.controls + m.extraControls
    fun find(id: String): ControlSpec? = all.firstOrNull { it.id == id }
    val pills = listOfNotNull(find("record"), find("pauseRec"), find("voice"))

    CallSurface(TcGradient) {
        val avatar = minOf(maxWidth * 0.32f, maxHeight * 0.14f).coerceAtLeast(76.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(36.dp))
                TcTitle(Modifier.entrance(titleIn))
                Spacer(Modifier.height(8.dp))
                Row(Modifier.entrance(titleIn), verticalAlignment = Alignment.CenterVertically) {
                    Text(TimeFormat.elapsed(m.elapsedMs), color = Color.White, fontSize = 17.sp, fontFamily = TcFont, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(16.dp).background(Color(0x66FFFFFF)))
                    Text(
                        if (m.held) "On hold" else m.caller.phone.ifBlank { "Unknown number" },
                        color = Color(0xCCFFFFFF), fontSize = 17.sp, fontFamily = TcFont,
                    )
                }
                RecordingBadge(m.recordingLabel, Color(0xFFFF8A80))
                Spacer(Modifier.height(24.dp))
                Box(Modifier.entrance(bodyIn)) { RippleAvatar(m.caller, avatar, rippling = !m.held) }
                Spacer(Modifier.height(8.dp))
                TcName(m.caller.title, Modifier.entrance(bodyIn))
                Spacer(Modifier.height(20.dp))
                ViewProfileButton({ showProfile = true }, Modifier.entrance(bodyIn))
                if (pills.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { pills.forEach { TcPill(it) } }
                }
            }
            Column(Modifier.fillMaxWidth().entrance(gridIn).padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TcCell(find("mute"), Modifier.weight(1f))
                    TcCell(find("keypad"), Modifier.weight(1f))
                    TcCell(find("speaker"), Modifier.weight(1f))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    TcCell(find("hold"), Modifier.weight(1f))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier.size(62.dp).clip(CircleShape).background(TcRed)
                                .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.height(6.dp))
                        Text("End", color = Color.White, fontSize = 15.sp, fontFamily = TcFont)
                    }
                    TcCell(find("message"), Modifier.weight(1f))
                }
            }
        }
    }
    if (showProfile) ProfileDialog(m.caller) { showProfile = false }
}

@Composable
private fun TcCell(spec: ControlSpec?, modifier: Modifier) {
    if (spec == null) {
        Spacer(modifier)
        return
    }
    Column(
        modifier.alpha(if (spec.enabled) 1f else 0.4f)
            .clickable(enabled = spec.enabled, onClickLabel = spec.label, role = Role.Button, onClick = spec.onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(if (spec.active) Color(0x40FFFFFF) else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) { Icon(spec.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(spec.label, color = Color.White, fontSize = 15.sp, fontFamily = TcFont, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun TcPill(spec: ControlSpec) {
    Row(
        Modifier.alpha(if (spec.enabled) 1f else 0.4f).clip(RoundedCornerShape(50))
            .background(if (spec.active) Color(0x55FFFFFF) else Color(0x26FFFFFF))
            .clickable(enabled = spec.enabled, onClickLabel = spec.label, role = Role.Button, onClick = spec.onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(spec.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(spec.label, color = Color.White, fontSize = 14.sp, fontFamily = TcFont, maxLines = 1)
    }
}
