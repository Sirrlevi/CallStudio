package com.callstudio.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "caller_profiles")
data class CallerProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    val photoUri: String? = null
)

@Entity(tableName = "scheduled_calls")
data class ScheduledCall(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerId: Long,
    val triggerAtMillis: Long,
    val voiceUri: String? = null,
    val repeatVoice: Boolean = false,
    val state: String = "SCHEDULED",
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "call_history")
data class CallHistory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerName: String,
    val phone: String,
    val outcome: String,
    val startedAtMillis: Long,
    val durationSeconds: Long,
    val scheduledCallId: Long? = null
)

@Entity(tableName = "recordings")
data class RecordingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val label: String,
    val callerName: String,
    val createdAtMillis: Long,
    val durationMillis: Long
)
