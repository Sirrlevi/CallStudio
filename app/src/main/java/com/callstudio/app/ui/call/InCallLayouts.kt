@file:OptIn(ExperimentalLayoutApi::class)

package com.callstudio.app.ui.call

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.callstudio.app.domain.TimeFormat
import com.callstudio.app.ui.components.CallerAvatar
import com.callstudio.app.ui.theme.CallTheme

@Composable
fun InCallScreen(theme: CallTheme, model: InCallModel) {
    when (theme) {
        CallTheme.PIXEL -> PixelInCall(model)
        CallTheme.ONE_UI -> OneUiInCall(model)
        CallTheme.IOS -> IosInCall(model)
        CallTheme.VIVO -> VivoInCall(model)
        CallTheme.MINIMAL_DARK -> MinimalDarkInCall(model)
        CallTheme.MINIMAL_LIGHT -> MinimalLightInCall(model)
    }
}

@Composable
private fun PixelInCall(m: InCallModel) {
    CallSurface(SolidColor(Color(0xFF1B211E))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(24.dp))
                CallerAvatar(m.caller.photoFile, m.caller.title, 96.dp, background = Color(0xFF2E4A3C), foreground = Color(0xFFCFE8D8))
                Spacer(Modifier.height(16.dp))
                Text(m.caller.title, color = Color(0xFFE3EAE4), fontSize = 30.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xFF9FD0B0), fontSize = 18.sp)
                RecordingBadge(m.recordingLabel, Color(0xFFFF8A80))
                Spacer(Modifier.height(28.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    maxItemsInEachRow = 3,
                ) {
                    m.controls.forEach {
                        ControlCell(
                            it, RoundedCornerShape(24.dp), 68.dp, Color(0xFF2E3833), Color(0xFFCDEBD6),
                            Color(0xFFE3EAE4), Color(0xFF16301F), Color(0xFFB8C2BB),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth(0.7f).height(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xFFE5484D))
                    .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.CallEnd, contentDescription = "End call", tint = Color.White) }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun OneUiInCall(m: InCallModel) {
    CallSurface(Brush.verticalGradient(listOf(Color(0xFF0F2A3D), Color(0xFF2B2B33)))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Text(m.caller.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xCCFFFFFF), fontSize = 18.sp)
            RecordingBadge(m.recordingLabel, Color(0xFFFF8A80))
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CallerAvatar(m.caller.photoFile, m.caller.title, 140.dp, background = Color(0xFF3A4A5A), foreground = Color.White)
            }
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                maxItemsInEachRow = 4,
            ) {
                m.controls.forEach {
                    ControlCell(it, CircleShape, 56.dp, Color(0x33FFFFFF), Color.White, Color.White, Color(0xFF111418), Color(0xCCFFFFFF), 11.sp)
                }
            }
            Spacer(Modifier.height(24.dp))
            RoundCallButton(Color(0xFFE5484D), Icons.Filled.CallEnd, "End call", m.onEnd, size = 72.dp)
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun IosInCall(m: InCallModel) {
    CallSurface(Brush.verticalGradient(listOf(Color(0xFF3A3A3C), Color(0xFF0B0B0C)))) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text(m.caller.title, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xCCFFFFFF), fontSize = 18.sp)
                RecordingBadge(m.recordingLabel, Color(0xFFFF6961))
                Spacer(Modifier.height(36.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    maxItemsInEachRow = 3,
                ) {
                    m.controls.forEach {
                        ControlCell(it, RoundedCornerShape(22.dp), 84.dp, Color(0x40FFFFFF), Color.White, Color.White, Color(0xFF1C1C1E), Color.White, 13.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            RoundCallButton(Color(0xFFFF3B30), Icons.Filled.CallEnd, "End call", m.onEnd, size = 76.dp)
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun VivoInCall(m: InCallModel) {
    val brush = Brush.verticalGradient(listOf(Color(0xFF0A57E8), Color(0xFF3F86FF), Color(0xFFBBD6FF), Color(0xFFF4F8FF)))
    CallSurface(brush) {
        Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(16.dp))
                CallerAvatar(
                    m.caller.photoFile, m.caller.title, 88.dp,
                    modifier = Modifier.border(3.dp, Color(0x88FFFFFF), CircleShape),
                    background = Color(0xFF6FA2FF), foreground = Color.White,
                )
                Spacer(Modifier.height(12.dp))
                Text(m.caller.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xE6FFFFFF), fontSize = 17.sp)
                RecordingBadge(m.recordingLabel, Color(0xFFFFD6D1))
                Spacer(Modifier.height(24.dp))
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Color(0xE6FFFFFF)).padding(vertical = 20.dp, horizontal = 12.dp)) {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        maxItemsInEachRow = 3,
                    ) {
                        m.controls.forEach {
                            ControlCell(it, CircleShape, 60.dp, Color(0xFFE8F0FF), Color(0xFF0A57E8), Color(0xFF0A57E8), Color.White, Color(0xFF1F2A44))
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(29.dp)).background(Color(0xFFFF3B30))
                    .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CallEnd, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("End call", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MinimalDarkInCall(m: InCallModel) {
    CallSurface(SolidColor(Color.Black)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 28.dp)) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
                Spacer(Modifier.height(20.dp))
                CallerAvatar(m.caller.photoFile, m.caller.title, 56.dp, background = Color(0xFF1C1C1C), foreground = Color(0xFFBDBDBD))
                Spacer(Modifier.height(20.dp))
                Text(m.caller.title, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.ExtraLight, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xFF9E9E9E), fontSize = 20.sp, fontWeight = FontWeight.Light)
                RecordingBadge(m.recordingLabel, Color(0xFFFF6B6B))
                Spacer(Modifier.height(28.dp))
                m.controls.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().alpha(if (c.enabled) 1f else 0.4f)
                            .clickable(enabled = c.enabled, onClickLabel = c.label, role = Role.Button, onClick = c.onClick)
                            .padding(vertical = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(c.label, color = if (c.active) Color.White else Color(0xFF9E9E9E), fontSize = 20.sp, fontWeight = FontWeight.Light)
                        Icon(c.icon, contentDescription = null, tint = if (c.active) Color(0xFF7BE0A0) else Color(0xFF757575), modifier = Modifier.size(22.dp))
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(60.dp).border(1.dp, Color(0xFFFF6B6B), RoundedCornerShape(4.dp))
                    .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                contentAlignment = Alignment.Center,
            ) { Text("End call", color = Color(0xFFFF6B6B), fontSize = 17.sp, fontWeight = FontWeight.Light) }
        }
    }
}

@Composable
private fun MinimalLightInCall(m: InCallModel) {
    CallSurface(SolidColor(Color(0xFFF3F5F7))) {
        Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(20.dp))
                CallerAvatar(m.caller.photoFile, m.caller.title, 96.dp, shape = RoundedCornerShape(26.dp), background = Color(0xFFDCE3EA), foreground = Color(0xFF42505E))
                Spacer(Modifier.height(16.dp))
                Text(m.caller.title, color = Color(0xFF111418), fontSize = 28.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(TimeFormat.elapsed(m.elapsedMs), color = Color(0xFF5B6773), fontSize = 17.sp)
                RecordingBadge(m.recordingLabel, Color(0xFFB3261E))
                Spacer(Modifier.height(24.dp))
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    maxItemsInEachRow = 2,
                ) {
                    m.controls.forEach { c ->
                        Row(
                            Modifier.weight(1f).height(56.dp).alpha(if (c.enabled) 1f else 0.4f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (c.active) Color(0xFF111418) else Color.White)
                                .border(1.dp, Color(0xFFD5DBE1), RoundedCornerShape(14.dp))
                                .clickable(enabled = c.enabled, onClickLabel = c.label, role = Role.Button, onClick = c.onClick)
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(c.icon, contentDescription = null, tint = if (c.active) Color.White else Color(0xFF111418), modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(c.label, color = if (c.active) Color.White else Color(0xFF111418), fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xFFB3261E))
                    .clickable(onClickLabel = "End call", role = Role.Button, onClick = m.onEnd),
                contentAlignment = Alignment.Center,
            ) { Text("End call", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Medium) }
            Spacer(Modifier.height(8.dp))
        }
    }
}
