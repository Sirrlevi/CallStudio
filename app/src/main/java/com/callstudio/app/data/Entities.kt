package com.callstudio.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.callstudio.app.domain.CallStatus

@Entity(tableName = "callers")
data class CallerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val phone: String,
    /** File name inside the app-private photos folder (not a URI, so it survives restarts). */
    val photoFile: String? = null,
    val createdAt: Long,
    val lastUsedAt: Long = 0,
)

/** One row per scheduled call; the status column doubles as call history once the call is finished. */
@Entity(tableName = "calls")
data class CallEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callerId: Long?,
    val callerName: String,
    val callerPhone: String,
    val callerPhotoFile: String?,
    val triggerAtMillis: Long,
    /** ISO local date-time for "at a time" schedules; lets the call follow time-zone changes. */
    val wallClock: String? = null,
    val status: String,
    val createdAt: Long,
    val answeredAtMillis: Long? = null,
    val endedAtMillis: Long? = null,
    val exactAlarm: Boolean = false,
    val endReason: String? = null,
)

@Entity(tableName = "recordings")
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    val createdAt: Long,
    val durationMs: Long,
    val sizeBytes: Long,
    val sampleRate: Int,
    val callerId: Long?,
    val callerName: String,
)

fun CallEventEntity.callStatus(): CallStatus = CallStatus.parse(status)
