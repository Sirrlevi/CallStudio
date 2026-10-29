package com.callstudio.app.ui.call

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Full-bleed background plus a content area that respects system bars, cutouts and is capped on wide screens. */
@Composable
fun CallSurface(
    background: Brush,
    underlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxWithConstraintsScope.() -> Unit,
) {
    Box(Modifier.fillMaxSize().background(background)) {
        underlay()
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.TopCenter,
        ) {
            BoxWithConstraints(Modifier.widthIn(max = 520.dp).fillMaxSize(), content = content)
        }
    }
}

@Composable
fun RoundCallButton(
    color: Color,
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 72.dp,
    tint: Color = Color.White,
    ring: Color? = null,
) {
    val core: @Composable () -> Unit = {
        Box(
            Modifier.size(size).clip(CircleShape).background(color)
                .clickable(onClickLabel = description, role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.45f))
        }
    }
    if (ring == null) core() else {
        Box(Modifier.size(size + 22.dp).clip(CircleShape).background(ring), contentAlignment = Alignment.Center) { core() }
    }
}

@Composable
fun PulseRing(color: Color, size: Dp, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val p by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "pulseValue",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(size)
                .graphicsLayer {
                    val s = 1f + 0.35f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.5f * (1f - p)
                }
                .clip(CircleShape).background(color)
        )
        content()
    }
}

@Composable
fun RecordingBadge(label: String?, color: Color) {
    if (label == null) return
    Row(Modifier.padding6(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, fontSize = 14.sp)
    }
}

private fun Modifier.padding6(): Modifier = this.then(Modifier.padding(top = 6.dp))

/** Icon-over-label control used by several themes; shape, size and colours make each theme different. */
@Composable
fun ControlCell(
    spec: ControlSpec,
    shape: Shape,
    size: Dp,
    bg: Color,
    activeBg: Color,
    fg: Color,
    activeFg: Color,
    labelColor: Color,
    labelSize: TextUnit = 12.sp,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.alpha(if (spec.enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(size).clip(shape).background(if (spec.active) activeBg else bg)
                .clickable(enabled = spec.enabled, onClickLabel = spec.label, role = Role.Button, onClick = spec.onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(spec.icon, contentDescription = spec.label, tint = if (spec.active) activeFg else fg, modifier = Modifier.size(size * 0.42f))
        }
        Spacer(Modifier.size(6.dp))
        Text(
            spec.label, color = labelColor, fontSize = labelSize, textAlign = TextAlign.Center,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = size + 24.dp),
        )
    }
}

fun numberLine(prefix: String, phone: String): String = if (phone.isBlank()) prefix else "$prefix · $phone"
