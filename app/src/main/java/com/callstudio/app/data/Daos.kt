package com.callstudio.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallerDao {
    @Query("SELECT * FROM callers ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<CallerEntity>>

    @Query("SELECT * FROM callers WHERE id = :id")
    suspend fun get(id: Long): CallerEntity?

    @Query("SELECT * FROM callers")
    suspend fun getAll(): List<CallerEntity>?

    @Query("SELECT * FROM callers ORDER BY lastUsedAt DESC, id LIMIT 1")
    suspend fun mostRecentlyUsed(): CallerEntity?

    @Insert
    suspend fun insert(caller: CallerEntity): Long

    @Update
    suspend fun update(caller: CallerEntity)

    @Query("DELETE FROM callers WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE callers SET lastUsedAt = :time WHERE id = :id")
    suspend fun touch(id: Long, time: Long)
}

@Dao
interface CallDao {
    @Insert
    suspend fun insert(event: CallEventEntity): Long

    @Update
    suspend fun update(event: CallEventEntity)

    @Query("SELECT * FROM calls WHERE id = :id")
    suspend fun get(id: Long): CallEventEntity?

    @Query("SELECT * FROM calls WHERE status = 'SCHEDULED' ORDER BY triggerAtMillis")
    fun observeScheduled(): Flow<List<CallEventEntity>>

    @Query("SELECT * FROM calls WHERE status = 'SCHEDULED'")
    suspend fun getScheduled(): List<CallEventEntity>

    @Query("SELECT * FROM calls WHERE status IN ('RINGING', 'ACTIVE')")
    suspend fun getLive(): List<CallEventEntity>

    @Query(
        "SELECT * FROM calls WHERE status NOT IN ('SCHEDULED', 'RINGING', 'ACTIVE') " +
            "ORDER BY COALESCE(endedAtMillis, triggerAtMillis) DESC"
    )
    fun observeHistory(): Flow<List<CallEventEntity>>

    @Query("DELETE FROM calls WHERE status NOT IN ('SCHEDULED', 'RINGING', 'ACTIVE')")
    suspend fun clearHistory()

    /** How many calls that haven't finished still need this voice file. */
    @Query("SELECT COUNT(*) FROM calls WHERE voiceUri = :uri AND status IN ('SCHEDULED', 'RINGING', 'ACTIVE')")
    suspend fun countActiveWithVoice(uri: String): Int
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<RecordingEntity>>

    @Query("SELECT * FROM recordings")
    suspend fun getAll(): List<RecordingEntity>

    @Insert
    suspend fun insert(recording: RecordingEntity): Long

    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun delete(id: Long)
}
