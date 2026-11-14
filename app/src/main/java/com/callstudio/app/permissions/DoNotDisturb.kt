package com.callstudio.app.permissions

import android.app.NotificationManager
import android.content.Context
import android.os.Build

/** What Do Not Disturb currently does to an incoming call. */
enum class DndLevel {
    /** Do Not Disturb is off. */
    OFF,

    /** On, but calls from anyone are allowed. */
    CALLS_FROM_ANYONE,

    /** On, and only some callers (contacts or starred contacts) are allowed. */
    CALLS_FROM_SOME,

    /** On, and calls are not allowed. */
    CALLS_BLOCKED,

    /** "Alarms only": every call is silenced, whatever the app does. */
    ALARMS_ONLY,

    /** "Total silence": every call is silenced, whatever the app does. */
    TOTAL_SILENCE,
}

/**
 * Do Not Disturb rules for an incoming call. The decisions are pure functions so they can be unit-tested; only [read]
 * talks to Android.
 *
 * A call notification on a channel that is allowed to override Do Not Disturb (see NotificationHelper) is shown and
 * rings even in "priority only" mode. Nothing can override "alarms only" or "total silence": a real call is silenced
 * there too.
 */
object DoNotDisturb {
    fun level(interruptionFilter: Int, priorityCategories: Int, callSenders: Int): DndLevel = when (interruptionFilter) {
        NotificationManager.INTERRUPTION_FILTER_PRIORITY -> when {
            priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_CALLS == 0 -> DndLevel.CALLS_BLOCKED
            callSenders == NotificationManager.Policy.PRIORITY_SENDERS_ANY -> DndLevel.CALLS_FROM_ANYONE
            else -> DndLevel.CALLS_FROM_SOME
        }
        NotificationManager.INTERRUPTION_FILTER_ALARMS -> DndLevel.ALARMS_ONLY
        NotificationManager.INTERRUPTION_FILTER_NONE -> DndLevel.TOTAL_SILENCE
        else -> DndLevel.OFF // ALL, and UNKNOWN (phones that don't report it)
    }

    /**
     * True when the app's own ringtone and vibration could be muted by Do Not Disturb but the system's call
     * notification would still get through. Then the system rings instead of the app.
     */
    fun shouldRingViaSystem(level: DndLevel, channelOverridesDnd: Boolean): Boolean =
        channelOverridesDnd && (level == DndLevel.CALLS_BLOCKED || level == DndLevel.CALLS_FROM_SOME)

    /** True when the call may well stay silent and the user should be told. */
    fun mayStaySilent(level: DndLevel, channelOverridesDnd: Boolean): Boolean = when (level) {
        DndLevel.OFF, DndLevel.CALLS_FROM_ANYONE -> false
        DndLevel.CALLS_FROM_SOME, DndLevel.CALLS_BLOCKED -> !channelOverridesDnd
        DndLevel.ALARMS_ONLY, DndLevel.TOTAL_SILENCE -> true
    }

    /** Short word for the call report. */
    fun describe(level: DndLevel): String = when (level) {
        DndLevel.OFF -> "off"
        DndLevel.CALLS_FROM_ANYONE -> "on(calls from anyone)"
        DndLevel.CALLS_FROM_SOME -> "on(calls from contacts only)"
        DndLevel.CALLS_BLOCKED -> "on(no calls)"
        DndLevel.ALARMS_ONLY -> "on(alarms only)"
        DndLevel.TOTAL_SILENCE -> "on(total silence)"
    }

    /** Reads the phone's current Do Not Disturb state. Never throws; an unreadable phone counts as "off". */
    fun read(context: Context): DndLevel = try {
        val manager = context.getSystemService(NotificationManager::class.java)
        val filter = manager.currentInterruptionFilter
        // The effective policy also covers Do Not Disturb schedules (Bedtime, Driving, ...), which have their own rules.
        val policy = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) manager.consolidatedNotificationPolicy else manager.notificationPolicy
        level(filter, policy.priorityCategories, policy.priorityCallSenders)
    } catch (e: Exception) {
        DndLevel.OFF
    }
}
