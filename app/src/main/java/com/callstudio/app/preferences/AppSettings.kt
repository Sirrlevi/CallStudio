package com.callstudio.app.preferences

import com.callstudio.app.ui.theme.CallTheme

enum class RingtoneSource { DEFAULT, SYSTEM, CUSTOM, SILENT }

enum class VoicePlayMode { AUTO_ONCE, AUTO_REPEAT, MANUAL }
enum class NotificationBadgeStyle { ONLINE_DOT, APP_LOGO }

/** Neon light that runs around the screen edge while a call is ringing. */
enum class EdgeGlowStyle(val label: String) {
    OFF("Off"),
    RAINBOW("Rainbow"),
    BLUE("Blue"),
    GREEN("Green"),
    PINK("Pink"),
}

data class AppSettings(
    val theme: CallTheme = CallTheme.PIXEL,
    val ringtoneSource: RingtoneSource = RingtoneSource.DEFAULT,
    val ringtoneUri: String? = null,
    val ringtoneLabel: String? = null,
    val vibrate: Boolean = true,
    val voiceMode: VoicePlayMode = VoicePlayMode.AUTO_ONCE,
    val startOnSpeaker: Boolean = false,
    val keepScreenOn: Boolean = true,
    val ringTimeoutSeconds: Int = 45,
    val recordingSampleRate: Int = 16_000,
    val notificationBadgeStyle: NotificationBadgeStyle = NotificationBadgeStyle.APP_LOGO,
    /** False hides the badge icon (the app icon or the green dot) on the caller photo in call notifications. */
    val notificationBadgeEnabled: Boolean = true,
    val lastCallerId: Long = -1L,
    /** When true, ring/vibrate exactly like a real call on this phone: silent, vibrate or ring (+ vibrate if the phone does). */
    val followPhoneRinger: Boolean = true,
    val edgeGlow: EdgeGlowStyle = EdgeGlowStyle.RAINBOW,
    /** Every call's timer starts at this many seconds instead of 0, for incoming and outgoing calls (0 = a normal timer). */
    val callTimerOffsetSeconds: Long = 0L,
)
