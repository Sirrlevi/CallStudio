package com.callstudio.app.fakeout

import android.content.Context

/** User choices for the fake outgoing call. All of them are local and optional. */
data class FakeOutgoingSettings(
    /** 1 or 2 shows "Calling via SIM n" like a dual-SIM phone; 0 hides the SIM text. */
    val simSlot: Int = 1,
    /** Plays the key tones of the number being dialed during "Calling…". */
    val dialTones: Boolean = true,
    /** Lets busy / no-answer calls sometimes end with a spoken network message. */
    val announcements: Boolean = true,
    /** One short buzz when the other side picks up. */
    val vibrateOnAnswer: Boolean = true,
    /** false: every outcome rings at least 20 seconds. true: busy and network drops may come earlier. */
    val earlyBusyAndDrop: Boolean = false,
    /** null = random. Set only for testing a specific ending. */
    val forced: FakeOutcome? = null,
)

/** Plain SharedPreferences: tiny, synchronous and independent of the rest of the app's settings. */
class FakeOutgoingPrefs(context: Context) {
    private val sp = context.getSharedPreferences("fake_outgoing", Context.MODE_PRIVATE)

    fun load(): FakeOutgoingSettings {
        val d = FakeOutgoingSettings()
        return FakeOutgoingSettings(
            simSlot = sp.getInt(SIM, d.simSlot).coerceIn(0, 2),
            dialTones = sp.getBoolean(DIAL, d.dialTones),
            announcements = sp.getBoolean(ANNOUNCE, d.announcements),
            vibrateOnAnswer = sp.getBoolean(VIBRATE, d.vibrateOnAnswer),
            earlyBusyAndDrop = sp.getBoolean(EARLY, d.earlyBusyAndDrop),
            forced = FakeOutcome.entries.firstOrNull { it.name == sp.getString(FORCED, null) },
        )
    }

    fun save(s: FakeOutgoingSettings) {
        sp.edit()
            .putInt(SIM, s.simSlot)
            .putBoolean(DIAL, s.dialTones)
            .putBoolean(ANNOUNCE, s.announcements)
            .putBoolean(VIBRATE, s.vibrateOnAnswer)
            .putBoolean(EARLY, s.earlyBusyAndDrop)
            .putString(FORCED, s.forced?.name)
            .apply()
    }

    /** The previous call's ending, so the next one is less likely to repeat it. */
    fun lastOutcome(): FakeOutcome? = FakeOutcome.entries.firstOrNull { it.name == sp.getString(LAST, null) }

    fun setLastOutcome(outcome: FakeOutcome) {
        sp.edit().putString(LAST, outcome.name).apply()
    }

    private companion object {
        const val SIM = "sim"
        const val DIAL = "dial_tones"
        const val ANNOUNCE = "announcements"
        const val VIBRATE = "vibrate"
        const val EARLY = "early"
        const val FORCED = "forced"
        const val LAST = "last_outcome"
    }
}
