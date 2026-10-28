package com.callstudio.app.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CallerDao {
    @Query("SELECT * FROM caller_profiles ORDER BY name COLLATE NOCASE") fun observeAll(): Flow<List<CallerProfile>>
    @Query("SELECT * FROM caller_profiles WHERE id = :id LIMIT 1") suspend fun findById(id: Long): CallerProfile?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: CallerProfile): Long
    @Update suspend fun update(item: CallerProfile)
    @Delete suspend fun delete(item: CallerProfile)
}

@Dao
interface ScheduledCallDao {
    @Query("SELECT * FROM scheduled_calls WHERE state = 'SCHEDULED' ORDER BY triggerAtMillis") fun observePending(): Flow<List<ScheduledCall>>
    @Query("SELECT * FROM scheduled_calls WHERE state = 'SCHEDULED' ORDER BY triggerAtMillis") suspend fun getPending(): List<ScheduledCall>
    @Query("SELECT * FROM scheduled_calls WHERE id = :id LIMIT 1") suspend fun findById(id: Long): ScheduledCall?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: ScheduledCall): Long
    @Update suspend fun update(item: ScheduledCall)
    @Query("UPDATE scheduled_calls SET state = :state WHERE id = :id") suspend fun updateState(id: Long, state: String)
    @Query("UPDATE scheduled_calls SET state = 'RINGING' WHERE id = :id AND state = 'SCHEDULED'") suspend fun claimForRinging(id: Long): Int
    @Delete suspend fun delete(item: ScheduledCall)
}

@Dao
interface CallHistoryDao {
    @Query("SELECT * FROM call_history ORDER BY startedAtMillis DESC") fun observeAll(): Flow<List<CallHistory>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: CallHistory): Long
    @Delete suspend fun delete(item: CallHistory)
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings ORDER BY createdAtMillis DESC") fun observeAll(): Flow<List<RecordingItem>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(item: RecordingItem): Long
    @Delete suspend fun delete(item: RecordingItem)
}
