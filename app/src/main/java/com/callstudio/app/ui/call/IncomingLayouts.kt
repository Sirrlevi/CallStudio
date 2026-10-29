package com.callstudio.app.ui.call

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.components.rememberPhoto
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Each theme has its own layout, type, spacing and controls; this is not one screen with different colours. */
@Composable
fun IncomingCallScreen(theme: CallTheme, caller: CallerUi, actions: IncomingActions) {
    when (theme) {
        CallTheme.PIXEL -> PixelIncoming(caller, actions)
        CallTheme.ONE_UI -> OneUiIncoming(caller, actions)
        CallTheme.IOS -> IosIncoming(caller, actions)
        CallTheme.VIVO -> VivoIncoming(caller, actions)
        CallTheme.MINIMAL_DARK -> MinimalDarkIncoming(caller, actions)
        CallTheme.MINIMAL_LIGHT -> MinimalLightIncoming(caller, actions)
    }
}

@Composable
private fun PixelIncoming(c: CallerUi, a: IncomingActions) {
    CallSurface(SolidColor(Color(0xFF1B211E))) {
        val avatar = minOf(maxWidth * 0.55f, maxHeight * 0.30f).coerceAtLeast(96.dp)
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(40.dp))
                Text(
                    c.title, color = Color(0xFFE3EAE4), fontSize = 36.sp, fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Normal, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Text(numberLine("Mobile", c.phone), color = Color(0xFFB8C2BB), fontSize = 16.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(36.dp))
                CallerAvatar(c.photoFile, c.title, avatar, background = Color(0xFF2E4A3C), foreground = Color(0xFFCFE8D8))
            }
            SwipePill(onDecline = a.onDecline, onAnswer = a.onAnswer)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun SwipePill(onDecline: () -> Unit, onAnswer: () -> Unit) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(42.dp)).background(Color(0xFF30363A))) {
        val thumbWidth = 132.dp
        val travel = with(LocalDensity.current) { ((maxWidth - thumbWidth) / 2f).toPx() }.coerceAtLeast(1f)
        val offset = remember { Animatable(0f) }
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Decline",
                Modifier.weight(1f).clickable(role = Role.Button, onClick = onDecline).padding(vertical = 28.dp),
                color = Color(0xFFF28B82), fontSize = 18.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.width(thumbWidth))
            Text(
                "Answer",
                Modifier.weight(1f).clickable(role = Role.Button, onClick = onAnswer).padding(vertical = 28.dp),
                color = Color(0xFF81C995), fontSize = 18.sp, textAlign = TextAlign.Center,
            )
        }
        Box(
            Modifier.align(Alignment.Center)
                .offset { IntOffset(offset.value.roundToInt(), 0) }
                .size(thumbWidth, 68.dp)
                .clip(RoundedCornerShape(34.dp))
                .background(Color.White)
                .pointerInput(travel) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                val o = offset.value
                                when {
                                    o >= travel * 0.7f -> onAnswer()
                                    o <= -travel * 0.7f -> onDecline()
                                }
                                offset.animateTo(0f)
                            }
                        },
                        onDragCancel = { scope.launch { offset.animateTo(0f) } },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            scope.launch { offset.snapTo((offset.value + dx).coerceIn(-travel, travel)) }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Drag to answer or decline", tint = Color(0xFF2E8B57))
        }
    }
}

@Composable
private fun OneUiIncoming(c: CallerUi, a: IncomingActions) {
    CallSurface(Brush.verticalGradient(listOf(Color(0xFF0F2A3D), Color(0xFF2B2B33)))) {
        val avatar = minOf(maxWidth * 0.42f, maxHeight * 0.22f).coerceAtLeast(84.dp)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(32.dp))
            Text("Incoming call", color = Color(0xB3FFFFFF), fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                c.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (c.phone.isNotBlank() && c.phone != c.title) {
                Text(c.phone, color = Color(0xB3FFFFFF), fontSize = 18.sp)
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PulseRing(color = Color(0xFF3DDC84), size = avatar) {
                    CallerAvatar(c.photoFile, c.title, avatar, background = Color(0xFF3A4A5A), foreground = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 36.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                RoundCallButton(Color(0xFF2EB872), Icons.Filled.Call, "Answer", a.onAnswer, size = 76.dp)
                RoundCallButton(Color(0xFFE5484D), Icons.Filled.CallEnd, "Decline", a.onDecline, size = 76.dp)
            }
        }
    }
}

@Composable
private fun IosIncoming(c: CallerUi, a: IncomingActions) {
    CallSurface(
        background = Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF0B0B0C))),
        underlay = {
            val photo = rememberPhoto(c.photoFile)
            if (photo != null) {
                Image(photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xAA000000), Color(0x22000000), Color(0xCC000000)))
                )
            )
        },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 28.dp, vertical = 24.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(36.dp))
                Text(
                    c.title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(numberLine("mobile", c.phone), color = Color(0xCCFFFFFF), fontSize = 17.sp)
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 28.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                LabelledCircle(Color(0xFFFF3B30), Icons.Filled.CallEnd, "Decline", a.onDecline)
                LabelledCircle(Color(0xFF34C759), Icons.Filled.Call, "Accept", a.onAnswer)
            }
        }
    }
}

@Composable
private fun LabelledCircle(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundCallButton(color, icon, label, onClick, size = 78.dp)
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color.White, fontSize = 15.sp)
    }
}

@Composable
private fun VivoIncoming(c: CallerUi, a: IncomingActions) {
    val brush = Brush.verticalGradient(
        listOf(Color(0xFF0A57E8), Color(0xFF3F86FF), Color(0xFFBBD6FF), Color(0xFFF4F8FF)),
    )
    CallSurface(brush) {
        val avatar = minOf(maxWidth * 0.40f, maxHeight * 0.20f).coerceAtLeast(84.dp)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(28.dp))
            Text("Incoming call", color = Color(0xDDFFFFFF), fontSize = 15.sp)
            Spacer(Modifier.height(20.dp))
            CallerAvatar(
                c.photoFile, c.title, avatar,
                modifier = Modifier.border(3.dp, Color(0x88FFFFFF), CircleShape),
                background = Color(0xFF6FA2FF), foreground = Color.White,
            )
            Spacer(Modifier.height(18.dp))
            Text(
                c.title, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (c.phone.isNotBlank() && c.phone != c.title) {
                Spacer(Modifier.height(6.dp))
                Text(c.phone, color = Color(0xE6FFFFFF), fontSize = 17.sp)
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().padding(bottom = 32.dp), horizontalArrangement = Arrangement.SpaceAround) {
                VivoButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "Decline", a.onDecline)
                VivoButton(Color(0xFF2EC25B), Icons.Filled.Call, "Answer", a.onAnswer)
            }
        }
    }
}

@Composable
private fun VivoButton(color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        RoundCallButton(color, icon, label, onClick, size = 72.dp, ring = Color(0x66607D9E))
        Spacer(Modifier.height(10.dp))
        Text(label, color = Color(0xFF1F2A44), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun MinimalDarkIncoming(c: CallerUi, a: IncomingActions) {
    CallSurface(SolidColor(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                CallerAvatar(c.photoFile, c.title, 72.dp, background = Color(0xFF1C1C1C), foreground = Color(0xFFBDBDBD))
                Spacer(Modifier.height(28.dp))
                Text("incoming call", color = Color(0xFF8A8A8A), fontSize = 13.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.height(6.dp))
                Text(
                    c.title, color = Color.White, fontSize = 42.sp, fontWeight = FontWeight.ExtraLight,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                if (c.phone.isNotBlank() && c.phone != c.title) {
                    Spacer(Modifier.height(6.dp))
                    Text(c.phone, color = Color(0xFF9E9E9E), fontSize = 18.sp, fontWeight = FontWeight.Light)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                OutlineAction("Decline", Color(0xFFFF6B6B), Icons.Filled.CallEnd, a.onDecline, Modifier.weight(1f))
                OutlineAction("Answer", Color(0xFF7BE0A0), Icons.Filled.Call, a.onAnswer, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OutlineAction(
    label: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit, modifier: Modifier,
) {
    Row(
        modifier.height(60.dp).border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button, onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = color, fontSize = 17.sp, fontWeight = FontWeight.Light)
    }
}

@Composable
private fun MinimalLightIncoming(c: CallerUi, a: IncomingActions) {
    CallSurface(SolidColor(Color(0xFFF3F5F7))) {
        val photo = minOf(maxWidth * 0.5f, maxHeight * 0.26f).coerceAtLeast(96.dp)
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                CallerAvatar(
                    c.photoFile, c.title, photo, shape = RoundedCornerShape(28.dp),
                    background = Color(0xFFDCE3EA), foreground = Color(0xFF42505E),
                )
                Spacer(Modifier.height(24.dp))
                Text(
                    c.title, color = Color(0xFF111418), fontSize = 30.sp, fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (c.phone.isNotBlank() && c.phone != c.title) {
                    Spacer(Modifier.height(4.dp))
                    Text(c.phone, color = Color(0xFF5B6773), fontSize = 17.sp)
                }
                Spacer(Modifier.height(4.dp))
                Text("is calling", color = Color(0xFF8793A0), fontSize = 14.sp)
            }
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFF111418))
                    .clickable(role = Role.Button, onClick = a.onAnswer),
                contentAlignment = Alignment.Center,
            ) { Text("Answer", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFFB9C2CC), RoundedCornerShape(16.dp))
                    .clickable(role = Role.Button, onClick = a.onDecline),
                contentAlignment = Alignment.Center,
            ) { Text("Decline", color = Color(0xFFB3261E), fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
