package com.callstudio.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

private val mint = Color(0xFF69C58B)
private val ink = Color(0xFF111713)

fun schemeFor(theme: String): ColorScheme = when (theme) {
    "Truecaller style" -> lightColorScheme(
        primary = Color(0xFF075FEA), onPrimary = Color.White, secondary = Color(0xFF246BDB),
        background = Color(0xFFEAF1FF), surface = Color.White, surfaceVariant = Color(0xFFD8E5FF)
    )
    "Samsung One UI" -> darkColorScheme(
        primary = Color(0xFF9DBBFF), onPrimary = Color(0xFF082D68), secondary = Color(0xFFBFC7DC),
        background = Color(0xFF10131B), surface = Color(0xFF181D29), surfaceVariant = Color(0xFF282F3D)
    )
    "iOS inspired" -> lightColorScheme(
        primary = Color(0xFF007AFF), onPrimary = Color.White, secondary = Color(0xFF4F83CC),
        background = Color(0xFFF4F6FA), surface = Color.White, surfaceVariant = Color(0xFFE6EAF1)
    )
    "Vivo Call UI", "Vivo inspired" -> darkColorScheme(
        primary = Color(0xFF00D4C4), onPrimary = Color(0xFF002A2A), secondary = Color(0xFF80EAE1),
        background = Color(0xFF071821), surface = Color(0xFF102A34), surfaceVariant = Color(0xFF1B3D48)
    )
    "Minimal dark" -> darkColorScheme(
        primary = Color(0xFFE0E0E0), onPrimary = Color(0xFF202020), secondary = Color(0xFFBDBDBD),
        background = Color(0xFF090909), surface = Color(0xFF141414), surfaceVariant = Color(0xFF262626)
    )
    "Minimal light" -> lightColorScheme(
        primary = Color(0xFF252525), onPrimary = Color.White, secondary = Color(0xFF666666),
        background = Color(0xFFF8F8F8), surface = Color.White, surfaceVariant = Color(0xFFEAEAEA)
    )
    else -> darkColorScheme(
        primary = mint, onPrimary = Color(0xFF00210F), secondary = Color(0xFF9CD7B0),
        background = ink, surface = Color(0xFF1A211D), surfaceVariant = Color(0xFF2A332D)
    )
}
