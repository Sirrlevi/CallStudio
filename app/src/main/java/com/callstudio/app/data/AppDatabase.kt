package com.callstudio.app.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CallerEntity::class, CallEventEntity::class, RecordingEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callerDao(): CallerDao
    abstract fun callDao(): CallDao
    abstract fun recordingDao(): RecordingDao
}
