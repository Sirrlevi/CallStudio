package com.callstudio.app.fakeout

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.random.Random

enum class AnnouncementKind { LINE_BUSY, NOT_ANSWERING }

data class Announcement(val locale: Locale, val text: String)

/** The recorded-style messages a network plays when a call can't be completed. Pure, so it is unit tested. */
object Announcements {
    fun pick(region: ToneRegion, kind: AnnouncementKind, random: Random): Announcement {
        val hindi = region == ToneRegion.SOUTH_ASIA && random.nextBoolean()
        return when {
            hindi -> Announcement(
                Locale.forLanguageTag("hi-IN"),
                if (kind == AnnouncementKind.LINE_BUSY) {
                    "आप जिनसे बात करना चाहते हैं वे अभी दूसरे कॉल पर व्यस्त हैं। कृपया थोड़ी देर बाद प्रयास करें।"
                } else {
                    "आप जिनसे बात करना चाहते हैं वे कॉल का उत्तर नहीं दे रहे हैं। कृपया थोड़ी देर बाद प्रयास करें।"
                },
            )
            else -> Announcement(
                when (region) {
                    ToneRegion.SOUTH_ASIA -> Locale.forLanguageTag("en-IN")
                    ToneRegion.NANP -> Locale.US
                    else -> Locale.UK
                },
                if (kind == AnnouncementKind.LINE_BUSY) {
                    "The person you are calling is busy on another call. Please try again later."
                } else {
                    "The person you are calling is not answering. Please try again later."
                },
            )
        }
    }
}

/**
 * Speaks network announcements with the phone's own text-to-speech engine, through the call audio path.
 * Best effort: with no engine or no voice for the language, [speak] reports failure and the call falls back to tones.
 */
class OperatorVoice(private val context: Context) {
    private var tts: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    /** Starts the engine (if needed) and waits briefly for it. Never throws. */
    suspend fun prepare(): Boolean {
        ready?.let { return it.await() }
        val gate = CompletableDeferred<Boolean>()
        ready = gate
        try {
            tts = TextToSpeech(context.applicationContext) { status -> gate.complete(status == TextToSpeech.SUCCESS) }
        } catch (e: Exception) {
            gate.complete(false)
        }
        val ok = withTimeoutOrNull(4_000L) { gate.await() } ?: false
        if (!ok) shutdown()
        return ok
    }

    /** Starts speaking. The result completes with true when the message finished, false when it couldn't be spoken. */
    fun speak(announcement: Announcement): CompletableDeferred<Boolean> {
        val result = CompletableDeferred<Boolean>()
        val engine = tts
        if (engine == null) {
            result.complete(false)
            return result
        }
        try {
            val language = engine.setLanguage(announcement.locale)
            if (language == TextToSpeech.LANG_MISSING_DATA || language == TextToSpeech.LANG_NOT_SUPPORTED) {
                result.complete(false)
                return result
            }
            engine.setAudioAttributes(attrs)
            engine.setSpeechRate(0.92f)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) { result.complete(true) }

                @Deprecated("Required by the abstract class on older API levels")
                override fun onError(utteranceId: String?) { result.complete(false) }
                override fun onError(utteranceId: String?, errorCode: Int) { result.complete(false) }
            })
            if (engine.speak(announcement.text, TextToSpeech.QUEUE_FLUSH, null, "fake_call_announcement") != TextToSpeech.SUCCESS) {
                result.complete(false)
            }
        } catch (e: Exception) {
            result.complete(false)
        }
        return result
    }

    fun stop() {
        runCatching { tts?.stop() }
    }

    /** Releases the speech engine. Called when the call is over. */
    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = null
    }
}
