package com.callstudio.app.preferences

import com.callstudio.app.ui.theme.CallTheme

enum class RingtoneSource { DEFAULT, SYSTEM, CUSTOM, SILENT }

enum class VoicePlayMode { AUTO_ONCE, AUTO_REPEAT, MANUAL }
enum class NotificationBadgeStyle { ONLINE_DOT, APP_LOGO }

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
    val lastCallerId: Long = -1L,
)
