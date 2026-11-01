package com.callstudio.app.outgoing

import java.util.Locale

data class TwilioConfig(val accountSid: String, val authToken: String, val fromNumber: String)

data class OutgoingSettings(
    val accountSid: String = "",
    val authToken: String = "",
    /** Your Twilio phone number (the call is placed from it), in +<country><number> form. */
    val fromNumber: String = "",
    /** Your own phone, rung when you want to join the call live after the clips. */
    val myNumber: String = "",
    val cloudName: String = "",
    val uploadPreset: String = "",
) {
    val twilioReady: Boolean get() = accountSid.isNotBlank() && authToken.isNotBlank() && fromNumber.isNotBlank()
    val uploadReady: Boolean get() = cloudName.isNotBlank() && uploadPreset.isNotBlank()
}

enum class OutgoingStatus(val label: String, val finished: Boolean) {
    QUEUED("Connecting…", false),
    RINGING("Ringing…", false),
    ANSWERED("Answered. Your clips are playing.", false),
    COMPLETED("Call ended", true),
    BUSY("The line was busy", true),
    NO_ANSWER("No answer", true),
    FAILED("The call failed", true),
    CANCELED("Call cancelled", true),
    UNKNOWN("Working…", false);

    companion object {
        fun fromTwilio(value: String?): OutgoingStatus = when (value?.lowercase(Locale.ROOT)) {
            "queued", "initiated" -> QUEUED
            "ringing" -> RINGING
            "in-progress" -> ANSWERED
            "completed" -> COMPLETED
            "busy" -> BUSY
            "no-answer" -> NO_ANSWER
            "failed" -> FAILED
            "canceled" -> CANCELED
            else -> UNKNOWN
        }
    }
}

/** Builds the call script Twilio runs once the person answers: clips in order, then hang up or connect you. */
object TwimlBuilder {
    fun build(clipUrls: List<String>, connectTo: String?): String {
        val sb = StringBuilder("<Response>")
        clipUrls.forEach { sb.append("<Play>").append(escape(it)).append("</Play>") }
        if (connectTo.isNullOrBlank()) {
            sb.append("<Hangup/>")
        } else {
            sb.append("<Dial><Number>").append(escape(connectTo)).append("</Number></Dial>")
        }
        return sb.append("</Response>").toString()
    }

    fun escape(text: String): String = text
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")
}

class OutgoingException(message: String) : Exception(message)
