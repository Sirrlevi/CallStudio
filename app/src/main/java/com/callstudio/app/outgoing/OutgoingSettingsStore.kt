package com.callstudio.app.outgoing

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.outgoingDataStore: DataStore<Preferences> by preferencesDataStore(name = "callstudio_outgoing")

class OutgoingSettingsStore(private val context: Context) {
    private object K {
        val SID = stringPreferencesKey("account_sid")
        val TOKEN = stringPreferencesKey("auth_token_encrypted")
        val FROM = stringPreferencesKey("from_number")
        val MINE = stringPreferencesKey("my_number")
        val CLOUD = stringPreferencesKey("cloud_name")
        val PRESET = stringPreferencesKey("upload_preset")
    }

    val settings: Flow<OutgoingSettings> = context.outgoingDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            OutgoingSettings(
                accountSid = p[K.SID].orEmpty(),
                authToken = p[K.TOKEN]?.let { SecretBox.decrypt(it) }.orEmpty(),
                fromNumber = p[K.FROM].orEmpty(),
                myNumber = p[K.MINE].orEmpty(),
                cloudName = p[K.CLOUD].orEmpty(),
                uploadPreset = p[K.PRESET].orEmpty(),
            )
        }

    suspend fun current(): OutgoingSettings = settings.first()

    suspend fun save(s: OutgoingSettings) {
        context.outgoingDataStore.edit {
            it[K.SID] = s.accountSid.trim()
            if (s.authToken.isBlank()) it.remove(K.TOKEN) else it[K.TOKEN] = SecretBox.encrypt(s.authToken.trim())
            it[K.FROM] = s.fromNumber.trim()
            it[K.MINE] = s.myNumber.trim()
            it[K.CLOUD] = s.cloudName.trim()
            it[K.PRESET] = s.uploadPreset.trim()
        }
    }

    suspend fun clear() {
        context.outgoingDataStore.edit { it.clear() }
    }
}
