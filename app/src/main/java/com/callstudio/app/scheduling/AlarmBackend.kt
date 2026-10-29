package com.callstudio.app.scheduling

interface AlarmBackend {
    fun canScheduleExact(): Boolean

    /** @return true when an exact alarm was set, false when only an approximate one could be. */
    fun set(eventId: Long, triggerAtMillis: Long, exact: Boolean): Boolean

    fun cancel(eventId: Long)
}

/** Chooses exact vs approximate delivery. One alarm per event id; setting again replaces the old one. */
class SchedulingEngine(private val backend: AlarmBackend) {
    fun canScheduleExact(): Boolean = backend.canScheduleExact()

    fun schedule(eventId: Long, triggerAtMillis: Long): Boolean =
        backend.set(eventId, triggerAtMillis, backend.canScheduleExact())

    fun cancel(eventId: Long) = backend.cancel(eventId)
}
