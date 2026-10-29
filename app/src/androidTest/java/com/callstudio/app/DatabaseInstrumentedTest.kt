package com.callstudio.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callstudio.app.data.AppDatabase
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.data.CallerEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseInstrumentedTest {
    private lateinit var db: AppDatabase

    @Before fun open() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun close() = db.close()

    private fun event(status: String, trigger: Long) = CallEventEntity(
        callerId = 1, callerName = "Alex", callerPhone = "555", callerPhotoFile = null,
        triggerAtMillis = trigger, status = status, createdAt = 0,
    )

    @Test fun scheduledAndHistoryQueriesSplitByStatus() = runTest {
        db.callerDao().insert(CallerEntity(name = "Alex", phone = "555", createdAt = 0))
        db.callDao().insert(event("SCHEDULED", 200))
        db.callDao().insert(event("SCHEDULED", 100))
        db.callDao().insert(event("ENDED", 50))
        db.callDao().insert(event("RINGING", 60))
        assertEquals(listOf(100L, 200L), db.callDao().observeScheduled().first().map { it.triggerAtMillis })
        assertEquals(1, db.callDao().observeHistory().first().size)
        assertEquals(1, db.callDao().getLive().size)
    }

    @Test fun clearHistoryKeepsScheduledAndLiveCalls() = runTest {
        db.callDao().insert(event("SCHEDULED", 1))
        db.callDao().insert(event("REJECTED", 2))
        db.callDao().clearHistory()
        assertEquals(1, db.callDao().getScheduled().size)
        assertEquals(0, db.callDao().observeHistory().first().size)
    }
}
