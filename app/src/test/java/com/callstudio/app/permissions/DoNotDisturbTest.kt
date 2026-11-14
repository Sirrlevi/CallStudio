package com.callstudio.app.permissions

import android.app.NotificationManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoNotDisturbTest {
    private val all = NotificationManager.INTERRUPTION_FILTER_ALL
    private val priority = NotificationManager.INTERRUPTION_FILTER_PRIORITY
    private val alarms = NotificationManager.INTERRUPTION_FILTER_ALARMS
    private val none = NotificationManager.INTERRUPTION_FILTER_NONE
    private val unknown = NotificationManager.INTERRUPTION_FILTER_UNKNOWN

    private val calls = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS
    private val messages = NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES
    private val anyone = NotificationManager.Policy.PRIORITY_SENDERS_ANY
    private val contacts = NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
    private val starred = NotificationManager.Policy.PRIORITY_SENDERS_STARRED

    @Test
    fun `do not disturb off or unreadable means no limit`() {
        assertEquals(DndLevel.OFF, DoNotDisturb.level(all, calls, contacts))
        assertEquals(DndLevel.OFF, DoNotDisturb.level(unknown, 0, 0))
    }

    @Test
    fun `priority mode follows the calls setting`() {
        assertEquals(DndLevel.CALLS_FROM_ANYONE, DoNotDisturb.level(priority, calls, anyone))
        assertEquals(DndLevel.CALLS_FROM_SOME, DoNotDisturb.level(priority, calls, contacts))
        assertEquals(DndLevel.CALLS_FROM_SOME, DoNotDisturb.level(priority, calls or messages, starred))
        assertEquals(DndLevel.CALLS_BLOCKED, DoNotDisturb.level(priority, messages, anyone))
        assertEquals(DndLevel.CALLS_BLOCKED, DoNotDisturb.level(priority, 0, contacts))
    }

    @Test
    fun `alarms only and total silence are their own levels`() {
        assertEquals(DndLevel.ALARMS_ONLY, DoNotDisturb.level(alarms, calls, anyone))
        assertEquals(DndLevel.TOTAL_SILENCE, DoNotDisturb.level(none, calls, anyone))
    }

    @Test
    fun `the system rings instead of the app only where Do Not Disturb would mute the app`() {
        assertTrue(DoNotDisturb.shouldRingViaSystem(DndLevel.CALLS_BLOCKED, channelOverridesDnd = true))
        assertTrue(DoNotDisturb.shouldRingViaSystem(DndLevel.CALLS_FROM_SOME, channelOverridesDnd = true))
        // Without permission to override, handing over to the system would not help.
        assertFalse(DoNotDisturb.shouldRingViaSystem(DndLevel.CALLS_BLOCKED, channelOverridesDnd = false))
        // Nothing to work around.
        assertFalse(DoNotDisturb.shouldRingViaSystem(DndLevel.OFF, channelOverridesDnd = true))
        assertFalse(DoNotDisturb.shouldRingViaSystem(DndLevel.CALLS_FROM_ANYONE, channelOverridesDnd = true))
        // Nothing can ring through these.
        assertFalse(DoNotDisturb.shouldRingViaSystem(DndLevel.ALARMS_ONLY, channelOverridesDnd = true))
        assertFalse(DoNotDisturb.shouldRingViaSystem(DndLevel.TOTAL_SILENCE, channelOverridesDnd = true))
    }

    @Test
    fun `the user is warned exactly when a call may stay silent`() {
        assertFalse(DoNotDisturb.mayStaySilent(DndLevel.OFF, channelOverridesDnd = false))
        assertFalse(DoNotDisturb.mayStaySilent(DndLevel.CALLS_FROM_ANYONE, channelOverridesDnd = false))
        assertTrue(DoNotDisturb.mayStaySilent(DndLevel.CALLS_FROM_SOME, channelOverridesDnd = false))
        assertTrue(DoNotDisturb.mayStaySilent(DndLevel.CALLS_BLOCKED, channelOverridesDnd = false))
        assertFalse(DoNotDisturb.mayStaySilent(DndLevel.CALLS_BLOCKED, channelOverridesDnd = true))
        assertTrue(DoNotDisturb.mayStaySilent(DndLevel.ALARMS_ONLY, channelOverridesDnd = true))
        assertTrue(DoNotDisturb.mayStaySilent(DndLevel.TOTAL_SILENCE, channelOverridesDnd = true))
    }

    @Test
    fun `every level has a report word`() {
        DndLevel.entries.forEach { assertTrue(DoNotDisturb.describe(it).isNotBlank()) }
    }
}
