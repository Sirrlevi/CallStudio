package com.callstudio.app.notifications

/** Unique PendingIntent request codes per (event, purpose) so one caller's actions never open another's call. */
object RequestCodes {
    const val ALARM = 0
    const val OPEN = 1
    const val ANSWER = 2
    const val DECLINE = 3
    const val HANG_UP = 4
    const val MUTE = 5

    fun forEvent(eventId: Long, purpose: Int): Int = ((eventId * 8 + purpose) and 0x7FFFFFFFL).toInt()
}
