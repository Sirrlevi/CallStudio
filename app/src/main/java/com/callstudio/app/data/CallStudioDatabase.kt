package com.callstudio.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CallerProfile::class, ScheduledCall::class, CallHistory::class, RecordingItem::class],
    version = 1,
    exportSchema = true
)
abstract class CallStudioDatabase : RoomDatabase() {
    abstract fun callerDao(): CallerDao
    abstract fun scheduledCallDao(): ScheduledCallDao
    abstract fun callHistoryDao(): CallHistoryDao
    abstract fun recordingDao(): RecordingDao

    companion object {
        @Volatile private var instance: CallStudioDatabase? = null
        fun get(context: Context): CallStudioDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                CallStudioDatabase::class.java,
                "callstudio.db"
            ).fallbackToDestructiveMigration().build().also { instance = it }
        }
    }
}
