package com.callstudio.app.outgoing

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal Twilio Voice REST client. The call runs in Twilio's cloud; this phone only starts, watches and ends it. */
class TwilioClient(private val config: TwilioConfig) {
    private val base = "https://api.twilio.com/2010-04-01/Accounts/${config.accountSid}"

    suspend fun startCall(to: String, twiml: String): String = withContext(Dispatchers.IO) {
        val reply = request(
            "$base/Calls.json", "POST",
            mapOf("To" to to, "From" to config.fromNumber, "Twiml" to twiml),
        )
        reply.optString("sid").ifBlank { throw OutgoingException("Twilio didn't return a call id.") }
    }

    suspend fun status(callSid: String): OutgoingStatus = withContext(Dispatchers.IO) {
        OutgoingStatus.fromTwilio(request("$base/Calls/$callSid.json", "GET", null).optString("status"))
    }

    suspend fun hangUp(callSid: String) {
        withContext(Dispatchers.IO) { request("$base/Calls/$callSid.json", "POST", mapOf("Status" to "completed")) }
    }

    private fun request(url: String, method: String, form: Map<String, String>?): JSONObject {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            val login = "${config.accountSid}:${config.authToken}".toByteArray()
            connection.setRequestProperty("Authorization", "Basic " + Base64.encodeToString(login, Base64.NO_WRAP))
            connection.setRequestProperty("Accept", "application/json")
            if (form != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val body = form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }.toByteArray()
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw OutgoingException(friendlyError(code, text))
            return JSONObject(text)
        } catch (e: IOException) {
            throw OutgoingException("Couldn't reach Twilio. Check your internet connection.")
        } catch (e: JSONException) {
            throw OutgoingException("Twilio sent a reply this app doesn't understand.")
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")

    private fun friendlyError(code: Int, body: String): String {
        val message = try {
            JSONObject(body).optString("message")
        } catch (e: JSONException) {
            ""
        }
        return when {
            code == 401 -> "Twilio rejected the Account SID or Auth Token. Check them in the setup."
            message.isNotBlank() -> message
            else -> "Twilio returned an error ($code)."
        }
    }
}
