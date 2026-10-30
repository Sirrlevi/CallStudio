package com.callstudio.app.domain

import java.util.Locale

data class Country(val iso: String, val dial: String) {
    val name: String get() = Locale("", iso).getDisplayCountry(Locale.getDefault()).ifBlank { iso }
    val flag: String get() = Countries.flag(iso)
    val dialLabel: String get() = Countries.dialLabel(dial)
}

/** Small built-in country table (ISO region + calling code). No network and no extra library. */
object Countries {
    private const val RAW =
        "AF93 AL355 DZ213 AS1684 AD376 AO244 AI1264 AG1268 AR54 AM374 AW297 AU61 AT43 AZ994 BS1242 BH973 BD880 " +
        "BB1246 BY375 BE32 BZ501 BJ229 BM1441 BT975 BO591 BA387 BW267 BR55 IO246 VG1284 BN673 BG359 BF226 BI257 " +
        "KH855 CM237 CA1 CV238 KY1345 CF236 TD235 CL56 CN86 CX61 CC61 CO57 KM269 CK682 CR506 HR385 CU53 CW599 " +
        "CY357 CZ420 CD243 DK45 DJ253 DM1767 DO1809 TL670 EC593 EG20 SV503 GQ240 ER291 EE372 ET251 FK500 FO298 " +
        "FJ679 FI358 FR33 GF594 PF689 GA241 GM220 GE995 DE49 GH233 GI350 GR30 GL299 GD1473 GP590 GU1671 GT502 " +
        "GG44 GN224 GW245 GY592 HT509 HN504 HK852 HU36 IS354 IN91 ID62 IR98 IQ964 IE353 IM44 IL972 IT39 CI225 " +
        "JM1876 JP81 JE44 JO962 KZ7 KE254 KI686 XK383 KW965 KG996 LA856 LV371 LB961 LS266 LR231 LY218 LI423 " +
        "LT370 LU352 MO853 MK389 MG261 MW265 MY60 MV960 ML223 MT356 MH692 MQ596 MR222 MU230 YT262 MX52 FM691 " +
        "MD373 MC377 MN976 ME382 MS1664 MA212 MZ258 MM95 NA264 NR674 NP977 NL31 NC687 NZ64 NI505 NE227 NG234 " +
        "NU683 NF672 KP850 MP1670 NO47 OM968 PK92 PW680 PS970 PA507 PG675 PY595 PE51 PH63 PL48 PT351 PR1787 " +
        "QA974 CG242 RE262 RO40 RU7 RW250 BL590 SH290 KN1869 LC1758 MF590 PM508 VC1784 WS685 SM378 ST239 SA966 " +
        "SN221 RS381 SC248 SL232 SG65 SX1721 SK421 SI386 SB677 SO252 ZA27 KR82 SS211 ES34 LK94 SD249 SR597 " +
        "SJ47 SZ268 SE46 CH41 SY963 TW886 TJ992 TZ255 TH66 TG228 TK690 TO676 TT1868 TN216 TR90 TM993 TC1649 " +
        "TV688 VI1340 UG256 UA380 AE971 GB44 US1 UY598 UZ998 VU678 VA379 VE58 VN84 WF681 EH212 YE967 ZM260 ZW263"

    /** Which region a shared calling code belongs to when it is ambiguous (for example +1 or +44). */
    private val primaryByDial = mapOf(
        "1" to "US", "7" to "RU", "44" to "GB", "61" to "AU", "47" to "NO", "212" to "MA", "262" to "RE", "590" to "GP",
    )

    val all: List<Country> = RAW.split(' ').filter { it.length > 2 }.map { Country(it.take(2), it.drop(2)) }
    private val byIso: Map<String, Country> = all.associateBy { it.iso }
    private val byDial: Map<String, Country> = all.groupBy { it.dial }.mapValues { (dial, list) ->
        list.firstOrNull { it.iso == primaryByDial[dial] } ?: list.first()
    }

    fun get(iso: String?): Country? = if (iso == null) null else byIso[iso.uppercase(Locale.ROOT)]

    fun nameOf(iso: String?): String? = get(iso)?.name

    fun defaultIso(): String = Locale.getDefault().country.takeIf { byIso.containsKey(it) } ?: "US"

    /** "+91", or "+1 264" for the shared +1 area-code regions. */
    fun dialLabel(dial: String): String = if (dial.startsWith("1") && dial.length == 4) "+1 ${dial.substring(1)}" else "+$dial"

    fun dialLabelOf(iso: String): String = get(iso)?.dialLabel ?: ""

    fun flag(iso: String): String =
        if (iso.length == 2 && iso.all { it in 'A'..'Z' }) {
            String(Character.toChars(0x1F1E6 + (iso[0] - 'A'))) + String(Character.toChars(0x1F1E6 + (iso[1] - 'A')))
        } else ""

    /** Splits "918059056588" into (India, "8059056588") using the longest matching calling code. */
    fun parseInternational(digits: String): Pair<Country, String>? {
        for (len in 4 downTo 1) {
            if (digits.length <= len) continue
            val match = byDial[digits.take(len)] ?: continue
            return match to digits.drop(len)
        }
        return null
    }
}
