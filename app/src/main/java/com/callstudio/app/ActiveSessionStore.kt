package com.callstudio.app

import android.content.Context

/** Persists only the metadata needed to restore the local in-app session UI after process recreation. */
object ActiveSessionStore {
    private const val FILE = "callstudio_active_session"
    private const val ACTIVE = "active"
    private const val SCHEDULED_ID = "scheduled_id"
    private const val CALLER_ID = "caller_id"
    private const val NAME = "name"
    private const val PHONE = "phone"
    private const val PHOTO = "photo"
    private const val VOICE = "voice"
    private const val REPEAT = "repeat"
    private const val STARTED = "started"

    fun start(
        context: Context, scheduledId: Long, callerId: Long, name: String, phone: String,
        photoUri: String?, voiceUri: String?, repeatVoice: Boolean, startedAt: Long
    ) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putBoolean(ACTIVE, true)
            .putLong(SCHEDULED_ID, scheduledId)
            .putLong(CALLER_ID, callerId)
            .putString(NAME, name)
            .putString(PHONE, phone)
            .putString(PHOTO, photoUri)
            .putString(VOICE, voiceUri)
            .putBoolean(REPEAT, repeatVoice)
            .putLong(STARTED, startedAt)
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun read(context: Context): ActiveSession? {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        if (!p.getBoolean(ACTIVE, false)) return null
        val name = p.getString(NAME, null) ?: return null
        return ActiveSession(
            scheduledId = p.getLong(SCHEDULED_ID, 0L),
            callerId = p.getLong(CALLER_ID, 0L),
            name = name,
            phone = p.getString(PHONE, "").orEmpty(),
            photoUri = p.getString(PHOTO, null),
            voiceUri = p.getString(VOICE, null),
            repeatVoice = p.getBoolean(REPEAT, false),
            startedAt = p.getLong(STARTED, System.currentTimeMillis())
        )
    }
}

data class ActiveSession(
    val scheduledId: Long,
    val callerId: Long,
    val name: String,
    val phone: String,
    val photoUri: String?,
    val voiceUri: String?,
    val repeatVoice: Boolean,
    val startedAt: Long
)
