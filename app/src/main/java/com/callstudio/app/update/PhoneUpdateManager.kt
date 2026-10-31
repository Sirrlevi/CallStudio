package com.callstudio.app.update

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class PhoneUpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkAssetId: Long,
    val sha256: String,
)

/** Authenticated release checker/downloader for the private Sirrlevi/CallStudio repository. */
class PhoneUpdateManager(private val context: Context) {
    fun checkForUpdate(token: String): PhoneUpdateInfo? {
        val releaseJson = JSONObject(
            requestBytes(
                "$API/repos/$OWNER/$REPOSITORY/releases/tags/$RELEASE_TAG",
                token,
                "application/vnd.github+json",
            ).toString(Charsets.UTF_8),
        )
        val assets = releaseJson.getJSONArray("assets")
        var metadataAssetId: Long? = null
        var apkAssetId: Long? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                METADATA_ASSET -> metadataAssetId = asset.getLong("id")
                APK_ASSET -> apkAssetId = asset.getLong("id")
            }
        }
        val metaId = metadataAssetId ?: throw IOException("The latest release has no update manifest.")
        val apkId = apkAssetId ?: throw IOException("The latest release has no Phone APK.")
        val metadata = JSONObject(
            requestBytes(
                "$API/repos/$OWNER/$REPOSITORY/releases/assets/$metaId",
                token,
                "application/octet-stream",
            ).toString(Charsets.UTF_8),
        )
        if (metadata.optString("packageName") != context.packageName) {
            throw IOException("The release belongs to a different Android app.")
        }
        val latestCode = metadata.getLong("versionCode")
        val installedCode = installedVersionCode()
        if (latestCode <= installedCode) return null
        return PhoneUpdateInfo(
            versionCode = latestCode,
            versionName = metadata.optString("versionName", "New version"),
            apkAssetId = apkId,
            sha256 = metadata.getString("sha256").lowercase(),
        )
    }

    fun downloadUpdate(token: String, update: PhoneUpdateInfo): File {
        val directory = File(context.cacheDir, "updates").apply { mkdirs() }
        val output = File(directory, "Phone-${update.versionName}.apk")
        val temporary = File(directory, "Phone-update.part")
        val connection = openConnection(
            "$API/repos/$OWNER/$REPOSITORY/releases/assets/${update.apkAssetId}",
            token,
            "application/octet-stream",
        )
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(connection, status)
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES) throw IOException("APK download is unexpectedly large.")
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("APK download exceeded the size limit.")
                        target.write(buffer, 0, count)
                    }
                    target.fd.sync()
                }
            }
            val actualHash = sha256(temporary)
            if (!actualHash.equals(update.sha256, ignoreCase = true)) {
                throw IOException("APK integrity check failed. Please check for the update again.")
            }
            if (output.exists()) output.delete()
            if (!temporary.renameTo(output)) throw IOException("Could not save the downloaded APK.")
            return output
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    @Suppress("DEPRECATION")
    private fun installedVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    private fun requestBytes(url: String, token: String, accept: String): ByteArray {
        val connection = openConnection(url, token, accept)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw httpError(connection, status)
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: String, token: String, accept: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Phone-Android-Updater")
        }
    }

    private fun httpError(connection: HttpURLConnection, status: Int): IOException {
        val detail = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull()
        return IOException(
            when (status) {
                401, 403, 404 -> "GitHub couldn't access the private Phone release. Check that your fine-grained token has Contents: Read-only for Sirrlevi/CallStudio."
                else -> "GitHub update request failed ($status). ${detail.orEmpty().take(180)}"
            },
        )
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val OWNER = "Sirrlevi"
        const val REPOSITORY = "CallStudio"
        const val RELEASE_TAG = "debug-latest"
        const val METADATA_ASSET = "phone-update.json"
        const val APK_ASSET = "Phone-debug.apk"
        const val API = "https://api.github.com"
        const val MAX_APK_BYTES = 100L * 1024L * 1024L
    }
}
