package com.callstudio.app.ui.call

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.callstudio.app.preferences.EdgeGlowStyle
import kotlin.math.floor

/**
 * Neon light that runs around the edge of the screen while a call rings, like the edge lighting phones show
 * for a real incoming call. Purely visual: it never takes touches and draws nothing when [style] is OFF.
 */
@Composable
fun EdgeGlow(style: EdgeGlowStyle, modifier: Modifier = Modifier) {
    if (style == EdgeGlowStyle.OFF) return
    val palette = style.palette()
    val transition = rememberInfiniteTransition(label = "edgeGlow")
    val travel by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "edgeGlowTravel",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "edgeGlowBreathe",
    )

    Canvas(modifier.fillMaxSize()) {
        // Phone screens have rounded corners; this radius follows them closely enough for the light to hug the edge.
        val corner = CornerRadius(46.dp.toPx())
        val stops = Array(STOP_COUNT + 1) { i ->
            val position = i.toFloat() / STOP_COUNT
            // The colour at each angle moves with [travel], so the light appears to run around the screen.
            position to paletteColor(palette, (position + travel) % 1f)
        }
        // Wider, fainter strokes first, then a bright thin line: stacked, they read as a soft neon glow.
        val layers = listOf(
            26.dp.toPx() to 0.10f,
            17.dp.toPx() to 0.16f,
            10.dp.toPx() to 0.28f,
            5.dp.toPx() to 0.55f,
            2.5.dp.toPx() to 1f,
        )
        for ((width, alpha) in layers) {
            val inset = if (width <= 3.dp.toPx()) width / 2f else 0f
            drawRoundRect(
                brush = Brush.sweepGradient(*stops, center = center),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = corner,
                alpha = alpha * breathe,
                style = Stroke(width = width),
            )
        }
    }
}

private const val STOP_COUNT = 24

private fun EdgeGlowStyle.palette(): List<Color> = when (this) {
    EdgeGlowStyle.RAINBOW -> listOf(
        Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFFD500F9), Color(0xFFFF1744), Color(0xFFFFEA00), Color(0xFF00E676),
    )
    EdgeGlowStyle.BLUE -> listOf(Color(0xFF00E5FF), Color(0xFF2979FF), Color(0xFF651FFF), Color(0xFF2979FF))
    EdgeGlowStyle.GREEN -> listOf(Color(0xFF00E676), Color(0xFFB2FF59), Color(0xFF00BFA5), Color(0xFF69F0AE))
    EdgeGlowStyle.PINK -> listOf(Color(0xFFFF4081), Color(0xFFD500F9), Color(0xFFFF1744), Color(0xFFE040FB))
    EdgeGlowStyle.OFF -> listOf(Color.Transparent)
}

/** Colour at [x] (0..1) around a palette that loops back to its first colour. */
private fun paletteColor(palette: List<Color>, x: Float): Color {
    val scaled = x * palette.size
    val index = floor(scaled).toInt()
    val fraction = scaled - index
    val from = palette[index % palette.size]
    val to = palette[(index + 1) % palette.size]
    return lerp(from, to, fraction)
}
