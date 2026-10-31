package com.callstudio.app.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.callstudio.app.ui.theme.CallTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "callstudio_settings")

class SettingsStore(private val context: Context) {
    private object K {
        val THEME = stringPreferencesKey("theme")
        val RINGTONE_SOURCE = stringPreferencesKey("ringtone_source")
        val RINGTONE_URI = stringPreferencesKey("ringtone_uri")
        val RINGTONE_LABEL = stringPreferencesKey("ringtone_label")
        val VIBRATE = booleanPreferencesKey("vibrate")
        val VOICE_MODE = stringPreferencesKey("voice_mode")
        val START_ON_SPEAKER = booleanPreferencesKey("start_on_speaker")
        val KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")
        val RING_TIMEOUT = intPreferencesKey("ring_timeout_seconds")
        val SAMPLE_RATE = intPreferencesKey("recording_sample_rate")
        val NOTIFICATION_BADGE = stringPreferencesKey("notification_badge_style")
        val LAST_CALLER = longPreferencesKey("last_caller_id")
    }

    val settings: Flow<AppSettings> = context.dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            val d = AppSettings()
            AppSettings(
                theme = CallTheme.fromName(p[K.THEME]),
                ringtoneSource = parse(p[K.RINGTONE_SOURCE], d.ringtoneSource),
                ringtoneUri = p[K.RINGTONE_URI],
                ringtoneLabel = p[K.RINGTONE_LABEL],
                vibrate = p[K.VIBRATE] ?: d.vibrate,
                voiceMode = parse(p[K.VOICE_MODE], d.voiceMode),
                startOnSpeaker = p[K.START_ON_SPEAKER] ?: d.startOnSpeaker,
                keepScreenOn = p[K.KEEP_SCREEN_ON] ?: d.keepScreenOn,
                ringTimeoutSeconds = p[K.RING_TIMEOUT] ?: d.ringTimeoutSeconds,
                recordingSampleRate = p[K.SAMPLE_RATE] ?: d.recordingSampleRate,
                notificationBadgeStyle = parse(p[K.NOTIFICATION_BADGE], d.notificationBadgeStyle),
                lastCallerId = p[K.LAST_CALLER] ?: d.lastCallerId,
            )
        }

    suspend fun current(): AppSettings = settings.first()

    suspend fun setTheme(theme: CallTheme) { context.dataStore.edit { it[K.THEME] = theme.name } }

    suspend fun setRingtone(source: RingtoneSource, uri: String?, label: String?) {
        context.dataStore.edit {
            it[K.RINGTONE_SOURCE] = source.name
            if (uri == null) it.remove(K.RINGTONE_URI) else it[K.RINGTONE_URI] = uri
            if (label == null) it.remove(K.RINGTONE_LABEL) else it[K.RINGTONE_LABEL] = label
        }
    }

    suspend fun setVibrate(on: Boolean) { context.dataStore.edit { it[K.VIBRATE] = on } }

    suspend fun setVoiceMode(mode: VoicePlayMode) { context.dataStore.edit { it[K.VOICE_MODE] = mode.name } }
    suspend fun setStartOnSpeaker(on: Boolean) { context.dataStore.edit { it[K.START_ON_SPEAKER] = on } }
    suspend fun setKeepScreenOn(on: Boolean) { context.dataStore.edit { it[K.KEEP_SCREEN_ON] = on } }
    suspend fun setRingTimeout(seconds: Int) { context.dataStore.edit { it[K.RING_TIMEOUT] = seconds } }
    suspend fun setRecordingSampleRate(hz: Int) { context.dataStore.edit { it[K.SAMPLE_RATE] = hz } }
    suspend fun setNotificationBadgeStyle(style: NotificationBadgeStyle) {
        context.dataStore.edit { it[K.NOTIFICATION_BADGE] = style.name }
        // NotificationHelper is also used by a foreground service, so mirror this one visual-only
        // preference for a synchronous read while constructing Android notifications.
        context.getSharedPreferences(NOTIFICATION_PREF_FILE, Context.MODE_PRIVATE)
            .edit().putString(NOTIFICATION_PREF_KEY, style.name).apply()
    }
    suspend fun setLastCaller(id: Long?) {
        context.dataStore.edit { if (id == null) it.remove(K.LAST_CALLER) else it[K.LAST_CALLER] = id }
    }

    companion object {
        const val NOTIFICATION_PREF_FILE = "notification_badge"
        const val NOTIFICATION_PREF_KEY = "style"
    }

    private inline fun <reified T : Enum<T>> parse(value: String?, default: T): T =
        enumValues<T>().firstOrNull { it.name == value } ?: default
}
