package com.callstudio.app

import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.outgoing.OutgoingSettings
import com.callstudio.app.outgoing.OutgoingStatus
import com.callstudio.app.outgoing.TwimlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutgoingTest {
    @Test fun clipsPlayInOrderThenTheCallHangsUp() {
        val script = TwimlBuilder.build(listOf("https://x.test/1.mp3", "https://x.test/2.mp3"), null)
        assertEquals(
            "<Response><Play>https://x.test/1.mp3</Play><Play>https://x.test/2.mp3</Play><Hangup/></Response>",
            script,
        )
    }

    @Test fun joiningLiveReplacesTheHangUpWithADialToYourPhone() {
        val script = TwimlBuilder.build(listOf("https://x.test/1.mp3"), "+14155550123")
        assertEquals("<Response><Play>https://x.test/1.mp3</Play><Dial><Number>+14155550123</Number></Dial></Response>", script)
        assertFalse(script.contains("Hangup"))
    }

    @Test fun linksWithSpecialCharactersAreEscaped() {
        val script = TwimlBuilder.build(listOf("https://x.test/a.mp3?x=1&y=<2>"), null)
        assertTrue(script.contains("x=1&amp;y=&lt;2&gt;"))
    }

    @Test fun twilioStatusesMapToFinishedAndUnfinished() {
        assertEquals(OutgoingStatus.QUEUED, OutgoingStatus.fromTwilio("queued"))
        assertEquals(OutgoingStatus.RINGING, OutgoingStatus.fromTwilio("ringing"))
        assertEquals(OutgoingStatus.ANSWERED, OutgoingStatus.fromTwilio("in-progress"))
        assertEquals(OutgoingStatus.NO_ANSWER, OutgoingStatus.fromTwilio("no-answer"))
        assertEquals(OutgoingStatus.UNKNOWN, OutgoingStatus.fromTwilio("something-new"))
        assertTrue(OutgoingStatus.COMPLETED.finished && OutgoingStatus.BUSY.finished && OutgoingStatus.FAILED.finished)
        assertFalse(OutgoingStatus.RINGING.finished || OutgoingStatus.ANSWERED.finished)
    }

    @Test fun dialableNumbersAreBuiltFromCountryAndDigits() {
        assertEquals("+918059056588", PhoneNumbers.e164("IN", "8059056588"))
        assertEquals("+918059056588", PhoneNumbers.e164("IN", "08059056588")) // trunk 0 dropped
        assertEquals("+12125551234", PhoneNumbers.e164("US", "2125551234"))
    }

    @Test fun tooShortOrUnknownNumbersAreRefused() {
        assertNull(PhoneNumbers.e164("US", "911"))
        assertNull(PhoneNumbers.e164("IN", "100"))
        assertNull(PhoneNumbers.e164("ZZ", "2125551234"))
    }

    @Test fun e164ValidationAcceptsOnlyPlusAndDigits() {
        assertTrue(PhoneNumbers.isE164("+14155550123"))
        assertFalse(PhoneNumbers.isE164("14155550123"))
        assertFalse(PhoneNumbers.isE164("+0123456789"))
        assertFalse(PhoneNumbers.isE164("+1 415 555 0123"))
    }

    @Test fun setupIsReadyOnlyWithTheRequiredTwilioFields() {
        assertFalse(OutgoingSettings().twilioReady)
        assertFalse(OutgoingSettings(accountSid = "AC1", authToken = "t").twilioReady)
        assertTrue(OutgoingSettings(accountSid = "AC1", authToken = "t", fromNumber = "+14155550123").twilioReady)
        assertFalse(OutgoingSettings(cloudName = "c").uploadReady)
        assertTrue(OutgoingSettings(cloudName = "c", uploadPreset = "p").uploadReady)
    }
}
