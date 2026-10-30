package com.callstudio.app

import com.callstudio.app.audio.VoiceLeases
import com.callstudio.app.data.CallerEntity
import com.callstudio.app.domain.ScheduleSpec
import com.callstudio.app.scheduling.ScheduleCoordinator
import com.callstudio.app.scheduling.ScheduleCoordinator.Outcome
import com.callstudio.app.scheduling.ScheduleCoordinator.Problem
import com.callstudio.app.scheduling.SchedulingEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.Locale
import java.time.ZoneId

class SchedulingTest {
    private var now = 1_700_000_000_000L
    private var zone: ZoneId = ZoneId.of("UTC")
    private val dao = FakeCallDao()
    private val backend = FakeAlarmBackend()
    private val fired = mutableListOf<Long>()
    private val coordinator = ScheduleCoordinator(dao, SchedulingEngine(backend), clock = { now }, zone = { zone }).apply {
        fireHandler = { id -> fired.add(id) }
    }
    private val alex = CallerEntity(id = 1, name = "Alex", phone = "555", createdAt = 0)
    private val sam = CallerEntity(id = 2, name = "Sam", phone = "556", createdAt = 0)

    @Test fun schedulingStoresRowAndSetsOneAlarm() = runTest {
        val o = coordinator.schedule(alex, ScheduleSpec.AfterSeconds(30)) as Outcome.Scheduled
        assertEquals(now + 30_000, backend.alarms[o.event.id])
        assertEquals(1, backend.alarms.size)
        assertEquals("SCHEDULED", dao.rows[o.event.id]!!.status)
        assertTrue(o.exact)
    }

    @Test fun sameCallerAtAboutTheSameTimeIsRejectedAsDuplicate() = runTest {
        coordinator.schedule(alex, ScheduleSpec.AfterMinutes(5))
        val again = coordinator.schedule(alex, ScheduleSpec.AfterSeconds(5 * 60 + 10))
        assertEquals(Outcome.Rejected(Problem.DUPLICATE), again)
        assertEquals(1, backend.alarms.size)
        assertTrue(coordinator.schedule(sam, ScheduleSpec.AfterMinutes(5)) is Outcome.Scheduled)
        assertEquals(2, backend.alarms.size)
    }

    @Test fun invalidInputIsRejectedAndNothingIsArmed() = runTest {
        assertEquals(Outcome.Rejected(Problem.NOT_POSITIVE), coordinator.schedule(alex, ScheduleSpec.AfterSeconds(0)))
        assertEquals(Outcome.Rejected(Problem.IN_THE_PAST), coordinator.schedule(alex, ScheduleSpec.AtLocal(LocalDateTime.of(2001, 1, 1, 0, 0))))
        assertTrue(backend.alarms.isEmpty())
        assertTrue(dao.rows.isEmpty())
    }

    @Test fun cancelRemovesAlarmAndMarksCancelledOnce() = runTest {
        val id = (coordinator.schedule(alex, ScheduleSpec.AfterMinutes(5)) as Outcome.Scheduled).event.id
        assertTrue(coordinator.cancel(id))
        assertTrue(backend.alarms.isEmpty())
        assertEquals("CANCELLED", dao.rows[id]!!.status)
        assertFalse(coordinator.cancel(id))
    }

    @Test fun reschedulingKeepsTheSameEventAndAlarm() = runTest {
        val id = (coordinator.schedule(alex, ScheduleSpec.AfterMinutes(5)) as Outcome.Scheduled).event.id
        val moved = coordinator.schedule(alex, ScheduleSpec.AfterMinutes(20), editingEventId = id) as Outcome.Scheduled
        assertEquals(id, moved.event.id)
        assertEquals(1, dao.rows.size)
        assertEquals(1, backend.alarms.size)
        assertEquals(now + 20 * 60_000, backend.alarms[id])
    }

    @Test fun withoutExactAccessAnApproximateAlarmIsUsedAndReported() = runTest {
        backend.exactAllowed = false
        val o = coordinator.schedule(alex, ScheduleSpec.AfterMinutes(5)) as Outcome.Scheduled
        assertFalse(o.exact)
        assertFalse(dao.rows[o.event.id]!!.exactAlarm)
        assertEquals(false, backend.lastExact)
    }

    @Test fun immediateCallsDoNotArmAnAlarm() = runTest {
        val o = coordinator.schedule(alex, ScheduleSpec.Immediate) as Outcome.Scheduled
        assertTrue(backend.alarms.isEmpty())
        assertEquals(now, o.event.triggerAtMillis)
    }

    @Test fun rebootRearmsFutureCallsAndMissesOverdueOnes() = runTest {
        val future = (coordinator.schedule(alex, ScheduleSpec.AfterMinutes(30)) as Outcome.Scheduled).event.id
        val overdue = (coordinator.schedule(sam, ScheduleSpec.AfterSeconds(60)) as Outcome.Scheduled).event.id
        val futureTrigger = backend.alarms[future]

        backend.alarms.clear() // a reboot wipes every alarm
        now += 5 * 60_000 // device comes back 5 minutes later

        coordinator.recover(recomputeWallClock = false)

        assertEquals(futureTrigger, backend.alarms[future])
        assertEquals("SCHEDULED", dao.rows[future]!!.status)
        assertEquals("MISSED", dao.rows[overdue]!!.status)
        assertNull(backend.alarms[overdue])
        assertTrue(fired.isEmpty())
    }

    @Test fun recentlyDueCallFiresOnRecovery() = runTest {
        val id = (coordinator.schedule(alex, ScheduleSpec.AfterSeconds(30)) as Outcome.Scheduled).event.id
        backend.alarms.clear()
        now += 60_000
        coordinator.recover(recomputeWallClock = false)
        assertEquals(listOf(id), fired)
    }

    @Test fun timeZoneChangeMovesWallClockCallsButNotRelativeOnes() = runTest {
        val wall = LocalDateTime.of(2024, 6, 1, 12, 0)
        val atTime = (coordinator.schedule(alex, ScheduleSpec.AtLocal(wall)) as Outcome.Scheduled).event
        val relative = (coordinator.schedule(sam, ScheduleSpec.AfterMinutes(60)) as Outcome.Scheduled).event
        val relativeBefore = backend.alarms[relative.id]

        zone = ZoneId.of("Asia/Tokyo")
        coordinator.recover(recomputeWallClock = true)

        val expected = wall.atZone(ZoneId.of("Asia/Tokyo")).toInstant().toEpochMilli()
        assertEquals(expected, backend.alarms[atTime.id])
        assertEquals(expected, dao.rows[atTime.id]!!.triggerAtMillis)
        assertEquals(relativeBefore, backend.alarms[relative.id])
    }

    @Test fun cancellingForACallerLeavesOthersAlone() = runTest {
        coordinator.schedule(alex, ScheduleSpec.AfterMinutes(5))
        val keep = (coordinator.schedule(sam, ScheduleSpec.AfterMinutes(5)) as Outcome.Scheduled).event.id
        coordinator.cancelForCaller(alex.id)
        assertEquals(listOf(keep), backend.alarms.keys.toList())
    }

    // ---- per-call voice clips and phone display ----------------------------------------------------------

    private val released = mutableListOf<String>()
    private val withLeases = ScheduleCoordinator(
        dao, SchedulingEngine(backend), clock = { now }, zone = { zone },
        leases = VoiceLeases(dao, release = { released.add(it) }),
    )

    @Test fun eachCallKeepsItsOwnVoiceClip() = runTest {
        val a = (withLeases.schedule(alex, ScheduleSpec.AfterMinutes(5), voice = ScheduleCoordinator.VoiceClip("content://a", "a.mp3")) as Outcome.Scheduled).event
        val b = (withLeases.schedule(sam, ScheduleSpec.AfterMinutes(10), voice = ScheduleCoordinator.VoiceClip("content://b", "b.mp3")) as Outcome.Scheduled).event
        val none = (withLeases.schedule(CallerEntity(id = 3, name = "Kim", phone = "1", createdAt = 0), ScheduleSpec.AfterMinutes(15)) as Outcome.Scheduled).event
        assertEquals("content://a", dao.rows[a.id]!!.voiceUri)
        assertEquals("content://b", dao.rows[b.id]!!.voiceUri)
        assertNull(dao.rows[none.id]!!.voiceUri)
    }

    @Test fun clipAccessIsReleasedOnlyWhenNoOtherUnfinishedCallUsesIt() = runTest {
        val clip = ScheduleCoordinator.VoiceClip("content://shared", "shared.mp3")
        val first = (withLeases.schedule(alex, ScheduleSpec.AfterMinutes(5), voice = clip) as Outcome.Scheduled).event.id
        val second = (withLeases.schedule(sam, ScheduleSpec.AfterMinutes(10), voice = clip) as Outcome.Scheduled).event.id
        withLeases.cancel(first)
        assertTrue(released.isEmpty())
        withLeases.cancel(second)
        assertEquals(listOf("content://shared"), released)
    }

    @Test fun changingTheClipOnAnEditReleasesTheOldOne() = runTest {
        val id = (withLeases.schedule(alex, ScheduleSpec.AfterMinutes(5), voice = ScheduleCoordinator.VoiceClip("content://old", "old")) as Outcome.Scheduled).event.id
        withLeases.schedule(alex, ScheduleSpec.AfterMinutes(6), editingEventId = id, voice = ScheduleCoordinator.VoiceClip("content://new", "new"))
        assertEquals(listOf("content://old"), released)
        assertEquals("content://new", dao.rows[id]!!.voiceUri)
    }

    @Test fun missedCallReleasesItsClip() = runTest {
        val id = (withLeases.schedule(alex, ScheduleSpec.AfterSeconds(30), voice = ScheduleCoordinator.VoiceClip("content://late", "late")) as Outcome.Scheduled).event.id
        backend.alarms.clear()
        now += 30 * 60_000
        withLeases.recover(recomputeWallClock = false)
        assertEquals("MISSED", dao.rows[id]!!.status)
        assertEquals(listOf("content://late"), released)
    }

    @Test fun callSnapshotKeepsCountryCodeSeparateFromTheNumber() = runTest {
        Locale.setDefault(Locale.US)
        val indian = CallerEntity(id = 9, name = "Sonu", phone = "8059056588", countryIso = "IN", createdAt = 0)
        val e = (coordinator.schedule(indian, ScheduleSpec.AfterMinutes(5)) as Outcome.Scheduled).event
        assertEquals("+91 80590 56588", dao.rows[e.id]!!.callerPhone)
        assertEquals("India", dao.rows[e.id]!!.callerCountry)
    }
}
