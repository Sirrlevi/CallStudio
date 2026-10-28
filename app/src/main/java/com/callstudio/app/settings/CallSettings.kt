package com.callstudio.app.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.callStudioDataStore by preferencesDataStore(name = "callstudio_settings")

object SettingKeys {
    val theme = stringPreferencesKey("theme")
    val ringtoneUri = stringPreferencesKey("ringtone_uri")
    val voiceUri = stringPreferencesKey("voice_uri")
    val vibration = booleanPreferencesKey("vibration")
    val repeatVoice = booleanPreferencesKey("repeat_voice")
}

data class AppSettings(
    val theme: String = "Pixel / Android",
    val ringtoneUri: String? = null,
    val voiceUri: String? = null,
    val vibration: Boolean = true,
    val repeatVoice: Boolean = false
)

fun Context.settingsFlow(): Flow<AppSettings> = callStudioDataStore.data.map { p: Preferences ->
    AppSettings(
        theme = p[SettingKeys.theme] ?: "Pixel / Android",
        ringtoneUri = p[SettingKeys.ringtoneUri],
        voiceUri = p[SettingKeys.voiceUri],
        vibration = p[SettingKeys.vibration] ?: true,
        repeatVoice = p[SettingKeys.repeatVoice] ?: false
    )
}

suspend fun Context.setThemePreference(theme: String) {
    callStudioDataStore.edit { it[SettingKeys.theme] = theme }
}

suspend fun Context.setRingtonePreference(uri: String?) {
    callStudioDataStore.edit { p -> if (uri == null) p.remove(SettingKeys.ringtoneUri) else p[SettingKeys.ringtoneUri] = uri }
    getSharedPreferences("callstudio_audio", Context.MODE_PRIVATE).edit().putString("ringtone_uri", uri).apply()
}

suspend fun Context.setVoicePreference(uri: String?) {
    callStudioDataStore.edit { p -> if (uri == null) p.remove(SettingKeys.voiceUri) else p[SettingKeys.voiceUri] = uri }
}

suspend fun Context.setVibrationPreference(enabled: Boolean) {
    callStudioDataStore.edit { it[SettingKeys.vibration] = enabled }
    getSharedPreferences("callstudio_audio", Context.MODE_PRIVATE).edit().putBoolean("vibration", enabled).apply()
}

suspend fun Context.setRepeatVoicePreference(enabled: Boolean) {
    callStudioDataStore.edit { it[SettingKeys.repeatVoice] = enabled }
}
