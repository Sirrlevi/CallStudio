package com.callstudio.app.outgoing

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Twilio can only play audio from a public web address, so a clip from this phone is first uploaded to the user's
 * own Cloudinary account (unsigned upload preset). The resulting link is public to anyone who has it.
 */
class ClipUploader(private val context: Context, private val cloudName: String, private val uploadPreset: String) {
    suspend fun upload(uri: Uri): String = withContext(Dispatchers.IO) {
        val boundary = "----CallStudio${System.nanoTime()}"
        val endpoint = "https://api.cloudinary.com/v1_1/${URLEncoder.encode(cloudName, "UTF-8")}/video/upload"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 90_000
            connection.setChunkedStreamingMode(0)
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            connection.outputStream.use { out ->
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"upload_preset\"\r\n\r\n$uploadPreset\r\n").toByteArray()
                )
                out.write(
                    ("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"clip.mp3\"\r\n" +
                        "Content-Type: application/octet-stream\r\n\r\n").toByteArray()
                )
                val input = context.contentResolver.openInputStream(uri) ?: throw FileNotFoundException()
                input.use { it.copyTo(out) }
                out.write("\r\n--$boundary--\r\n".toByteArray())
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw OutgoingException(uploadError(text))
            JSONObject(text).optString("secure_url").ifBlank { throw OutgoingException("The upload didn't return a link.") }
        } catch (e: FileNotFoundException) {
            throw OutgoingException("That audio file isn't available any more. Add it again.")
        } catch (e: SecurityException) {
            throw OutgoingException("That audio file isn't available any more. Add it again.")
        } catch (e: IOException) {
            throw OutgoingException("The upload failed. Check your internet connection and try again.")
        } catch (e: JSONException) {
            throw OutgoingException("The upload service sent a reply this app doesn't understand.")
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadError(body: String): String {
        val message = try {
            JSONObject(body).optJSONObject("error")?.optString("message").orEmpty()
        } catch (e: JSONException) {
            ""
        }
        return if (message.isBlank()) "The upload was refused. Check the cloud name and upload preset in the setup."
        else "Upload failed: $message"
    }
}
