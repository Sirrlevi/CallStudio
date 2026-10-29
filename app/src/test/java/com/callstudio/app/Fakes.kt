package com.callstudio.app

import com.callstudio.app.data.CallDao
import com.callstudio.app.data.CallEventEntity
import com.callstudio.app.scheduling.AlarmBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeAlarmBackend(var exactAllowed: Boolean = true) : AlarmBackend {
    val alarms = linkedMapOf<Long, Long>()
    var lastExact: Boolean? = null

    override fun canScheduleExact(): Boolean = exactAllowed

    override fun set(eventId: Long, triggerAtMillis: Long, exact: Boolean): Boolean {
        alarms[eventId] = triggerAtMillis // one alarm per event id, like PendingIntent.FLAG_UPDATE_CURRENT
        lastExact = exact
        return exact
    }

    override fun cancel(eventId: Long) {
        alarms.remove(eventId)
    }
}

class FakeCallDao : CallDao {
    val rows = linkedMapOf<Long, CallEventEntity>()
    private var nextId = 1L

    override suspend fun insert(event: CallEventEntity): Long {
        val id = nextId++
        rows[id] = event.copy(id = id)
        return id
    }

    override suspend fun update(event: CallEventEntity) {
        rows[event.id] = event
    }

    override suspend fun get(id: Long): CallEventEntity? = rows[id]

    override fun observeScheduled(): Flow<List<CallEventEntity>> = flowOf(rows.values.filter { it.status == "SCHEDULED" })

    override suspend fun getScheduled(): List<CallEventEntity> = rows.values.filter { it.status == "SCHEDULED" }

    override suspend fun getLive(): List<CallEventEntity> = rows.values.filter { it.status == "RINGING" || it.status == "ACTIVE" }

    override fun observeHistory(): Flow<List<CallEventEntity>> =
        flowOf(rows.values.filter { it.status !in listOf("SCHEDULED", "RINGING", "ACTIVE") })

    override suspend fun clearHistory() {
        rows.entries.removeAll { it.value.status !in listOf("SCHEDULED", "RINGING", "ACTIVE") }
    }
}
