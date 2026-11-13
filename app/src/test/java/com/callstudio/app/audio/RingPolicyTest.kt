package com.callstudio.app.audio

import com.callstudio.app.audio.RingPolicy.Mode
import com.callstudio.app.audio.RingPolicy.Plan
import org.junit.Assert.assertEquals
import org.junit.Test

/** The ring/vibrate rules a real incoming call follows on this phone. */
class RingPolicyTest {
    private fun plan(
        mode: Mode,
        followPhone: Boolean = true,
        appVibrate: Boolean = true,
        phoneVibrates: Boolean? = null,
        ringtoneSilent: Boolean = false,
    ) = RingPolicy.plan(mode, followPhone, appVibrate, phoneVibrates, ringtoneSilent)

    @Test fun silentModeDoesNothing() {
        assertEquals(Plan(vibrate = false, sound = false), plan(Mode.SILENT, phoneVibrates = true))
        assertEquals(Plan(vibrate = false, sound = false), plan(Mode.SILENT, followPhone = false, appVibrate = true))
    }

    @Test fun vibrateModeVibratesOnly() {
        assertEquals(Plan(vibrate = true, sound = false), plan(Mode.VIBRATE, phoneVibrates = false))
        assertEquals(Plan(vibrate = true, sound = false), plan(Mode.VIBRATE, appVibrate = false))
    }

    @Test fun ringModeFollowsThePhonesVibrateForCallsSwitch() {
        assertEquals(Plan(vibrate = false, sound = true), plan(Mode.NORMAL, phoneVibrates = false))
        assertEquals(Plan(vibrate = true, sound = true), plan(Mode.NORMAL, phoneVibrates = true))
    }

    @Test fun ringModeUsesTheAppSwitchWhenThePhoneDoesNotSayIt() {
        assertEquals(Plan(vibrate = true, sound = true), plan(Mode.NORMAL, appVibrate = true, phoneVibrates = null))
        assertEquals(Plan(vibrate = false, sound = true), plan(Mode.NORMAL, appVibrate = false, phoneVibrates = null))
    }

    @Test fun notFollowingThePhoneUsesTheAppSwitchInRingMode() {
        assertEquals(Plan(vibrate = false, sound = true), plan(Mode.NORMAL, followPhone = false, appVibrate = false, phoneVibrates = true))
        assertEquals(Plan(vibrate = true, sound = true), plan(Mode.NORMAL, followPhone = false, appVibrate = true, phoneVibrates = false))
    }

    @Test fun aSilentRingtoneStillVibratesWhenThePhoneWouldVibrate() {
        assertEquals(Plan(vibrate = true, sound = false), plan(Mode.NORMAL, phoneVibrates = true, ringtoneSilent = true))
        assertEquals(Plan(vibrate = false, sound = false), plan(Mode.NORMAL, phoneVibrates = false, ringtoneSilent = true))
    }
}
