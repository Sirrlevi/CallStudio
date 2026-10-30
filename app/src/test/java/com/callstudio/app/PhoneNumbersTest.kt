package com.callstudio.app

import com.callstudio.app.domain.Countries
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.ui.theme.CallTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhoneNumbersTest {
    @Test fun internationalPrefixSwitchesTheCountry() {
        assertEquals(PhoneNumbers.Parsed("IN", "8059056588"), PhoneNumbers.normalize("+91 80590 56588", "US"))
        assertEquals(PhoneNumbers.Parsed("IN", "8059056588"), PhoneNumbers.normalize("0091 80590 56588", "US"))
        assertEquals(PhoneNumbers.Parsed("GB", "2071838750"), PhoneNumbers.normalize("+44 20 7183 8750", "IN"))
    }

    @Test fun numbersWithoutPrefixKeepTheSelectedCountry() {
        assertEquals(PhoneNumbers.Parsed("IN", "8059056588"), PhoneNumbers.normalize("80590 56588", "IN"))
        assertEquals(PhoneNumbers.Parsed("US", "2125551234"), PhoneNumbers.normalize("(212) 555-1234", "US"))
    }

    @Test fun sharedCallingCodesPickTheMainRegionButAreaCodeRegionsStillMatch() {
        assertEquals("US", PhoneNumbers.normalize("+1 650 253 0000", "IN").iso)
        assertEquals("AI", PhoneNumbers.normalize("+1 264 123 4567", "US").iso)
        assertEquals("RU", PhoneNumbers.normalize("+7 912 345 67 89", "IN").iso)
    }

    @Test fun displayShowsCountryCodeApartFromTheNumber() {
        assertEquals("+91 80590 56588", PhoneNumbers.display("IN", "8059056588"))
        assertEquals("+1 212 555 1234", PhoneNumbers.display("US", "2125551234"))
        assertEquals("+1 264 123 4567", PhoneNumbers.display("AI", "1234567"))
        assertEquals("", PhoneNumbers.display("IN", ""))
        assertEquals("555", PhoneNumbers.display("", "555")) // older callers without a country
    }

    @Test fun unusualInputIsLeftAsTyped() {
        assertEquals("12ab34", PhoneNumbers.group("12ab34", "IN"))
        assertEquals("112", PhoneNumbers.group("112", "IN"))
    }

    @Test fun dialableKeepsOnlyDigitsAndPlus() {
        assertEquals("+918059056588", PhoneNumbers.dialable("+91 80590 56588"))
    }

    @Test fun countryTableIsConsistent() {
        assertEquals(Countries.all.size, Countries.all.map { it.iso }.toSet().size)
        assertEquals("+91", Countries.dialLabelOf("IN"))
        assertEquals("+1 264", Countries.dialLabelOf("AI"))
        assertEquals(4, Countries.flag("IN").length)
        assertNotNull(Countries.get("in"))
        assertTrue(Countries.all.size > 200)
    }

    @Test fun truecallerStyleThemeIsAvailable() {
        assertTrue(CallTheme.entries.contains(CallTheme.TRUECALLER))
        assertEquals(CallTheme.TRUECALLER, CallTheme.fromName("TRUECALLER"))
        assertEquals(CallTheme.PIXEL, CallTheme.fromName("nonsense"))
    }
}
