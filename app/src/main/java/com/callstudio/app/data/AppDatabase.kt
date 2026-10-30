package com.callstudio.app.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CallerEntity::class, CallEventEntity::class, RecordingEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun callerDao(): CallerDao
    abstract fun callDao(): CallDao
    abstract fun recordingDao(): RecordingDao
}

object Migrations {
    /** v2: separate country code, per-call voice clip. Existing callers and history are kept. */
    val V1_TO_V2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE callers ADD COLUMN countryIso TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE calls ADD COLUMN callerCountry TEXT")
            db.execSQL("ALTER TABLE calls ADD COLUMN voiceUri TEXT")
            db.execSQL("ALTER TABLE calls ADD COLUMN voiceLabel TEXT")
        }
    }
}
