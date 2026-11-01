package com.callstudio.app.domain

object PhoneNumbers {
    data class Parsed(val iso: String, val national: String)

    private val fiveFive = setOf("IN", "PK", "NP")

    /**
     * Turns what the user typed (or a contact's number) into a country plus national digits.
     * "+91 80590 56588" and "0091 80590 56588" both become India + 8059056588. Without a prefix the
     * [selectedIso] country is kept.
     */
    fun normalize(input: String, selectedIso: String): Parsed {
        val raw = input.trim()
        val digits = raw.filter { it.isDigit() }
        val international = raw.startsWith("+") || raw.startsWith("00")
        if (!international) return Parsed(selectedIso, digits)
        val rest = if (raw.startsWith("+")) digits else digits.removePrefix("00")
        val parsed = Countries.parseInternational(rest)
        return if (parsed != null) Parsed(parsed.first.iso, parsed.second) else Parsed(selectedIso, rest)
    }

    /** Groups digits the way people read them: "80590 56588", "212 555 1234". Unusual input is left as typed. */
    fun group(national: String, iso: String): String {
        val digits = national.filter { it.isDigit() }
        if (digits.isEmpty() || digits != national.replace(" ", "")) return national
        return when {
            digits.length == 10 && iso in fiveFive -> "${digits.take(5)} ${digits.drop(5)}"
            digits.length == 10 -> "${digits.take(3)} ${digits.substring(3, 6)} ${digits.drop(6)}"
            digits.length == 7 -> "${digits.take(3)} ${digits.drop(3)}"
            digits.length == 9 -> "${digits.take(3)} ${digits.substring(3, 6)} ${digits.drop(6)}"
            digits.length == 11 -> "${digits.take(3)} ${digits.substring(3, 7)} ${digits.drop(7)}"
            digits.length <= 6 -> digits
            else -> digits.chunked(4).joinToString(" ")
        }
    }

    /** "+91 80590 56588": the country code is always separated from the number. */
    fun display(iso: String, national: String): String = when {
        national.isBlank() -> ""
        iso.isBlank() || Countries.get(iso) == null -> national
        else -> "${Countries.dialLabelOf(iso)} ${group(national, iso)}"
    }

    private val e164Pattern = Regex("^\\+[1-9]\\d{6,14}$")

    fun isE164(value: String): Boolean = e164Pattern.matches(value.trim())

    /**
     * "+<calling code><number>" for dialing through a provider, or null when the number is too short to be a
     * real number (this also keeps short and emergency codes out). One leading trunk "0" is dropped.
     */
    fun e164(iso: String, national: String): String? {
        val country = Countries.get(iso) ?: return null
        val digits = national.filter { it.isDigit() }
        val trimmed = if (digits.startsWith("0") && iso != "IT") digits.drop(1) else digits
        if (trimmed.length < 6) return null
        val result = "+${country.dial}$trimmed"
        return if (isE164(result)) result else null
    }

    /** Digits with a leading + for messaging apps. */
    fun dialable(displayNumber: String): String = displayNumber.filter { it.isDigit() || it == '+' }
}
