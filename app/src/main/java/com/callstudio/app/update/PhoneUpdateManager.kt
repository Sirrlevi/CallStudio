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
    /** What changed, taken from the commit message of the build. May be empty. */
    val notes: String = "",
    /** Size of the APK in bytes, or 0 when GitHub didn't say. */
    val sizeBytes: Long = 0L,
)

/** Which GitHub request failed; the same HTTP status means different things at each step. */
enum class Stage { REPOSITORY, RELEASE, FILE }

/**
 * Turns a GitHub error into one sentence that says what to change. [githubMessage] is GitHub's own explanation
 * (the "message" field of its JSON error), appended so nothing is hidden.
 */
fun updateErrorMessage(status: Int, stage: Stage, githubMessage: String = ""): String {
    val text = when {
        status == 401 ->
            "GitHub says this token isn't valid. It may have expired, been revoked, or been pasted with a character missing. Create a new token and paste it again."
        status == 404 && stage == Stage.REPOSITORY ->
            "This token can't see Sirrlevi/CallStudio. When creating the token, set Resource owner to Sirrlevi and Repository access to Only select repositories, then choose CallStudio."
        status == 404 && stage == Stage.RELEASE ->
            "The token works, but there is no \"debug-latest\" release yet. It appears once a build has finished on master."
        status == 404 ->
            "GitHub couldn't find the update file. A new build may still be uploading; try again in a minute."
        status == 403 && githubMessage.contains("rate limit", ignoreCase = true) ->
            "GitHub's request limit was reached. Try again in a few minutes."
        status == 403 ->
            "The token can see the repository but isn't allowed to read its releases. Edit the token and set Repository permissions > Contents to Read-only."
        else -> "GitHub update request failed ($status)."
    }
    val extra = githubMessage.trim().take(160)
    return if (extra.isEmpty()) text else "$text (GitHub says: $extra)"
}

/** Authenticated release checker/downloader for the private Sirrlevi/CallStudio repository. */
class PhoneUpdateManager(private val context: Context) {
    fun installedVersionName(): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    @Suppress("DEPRECATION")
    fun installedVersionCode(): Long {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    }

    /** Returns the newer release, or null when this install is already the latest. */
    fun checkForUpdate(token: String): PhoneUpdateInfo? {
        // Step 1: can this token see the repository at all? A failure here means the token itself is the problem,
        // which is a different fix from "the release isn't there yet" or "the token can't read releases".
        requestBytes("$API/repos/$OWNER/$REPOSITORY", token, "application/vnd.github+json", Stage.REPOSITORY)
        // Step 2: the release that holds the latest build.
        val releaseJson = JSONObject(
            requestBytes(
                "$API/repos/$OWNER/$REPOSITORY/releases/tags/$RELEASE_TAG",
                token,
                "application/vnd.github+json",
                Stage.RELEASE,
            ).toString(Charsets.UTF_8),
        )
        val assets = releaseJson.getJSONArray("assets")
        var metadataAssetId: Long? = null
        var apkAssetId: Long? = null
        var apkSize = 0L
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            when (asset.optString("name")) {
                METADATA_ASSET -> metadataAssetId = asset.getLong("id")
                APK_ASSET -> {
                    apkAssetId = asset.getLong("id")
                    apkSize = asset.optLong("size", 0L)
                }
            }
        }
        val metaId = metadataAssetId ?: throw IOException("The latest release has no update manifest.")
        val apkId = apkAssetId ?: throw IOException("The latest release has no Phone APK.")
        val metadata = JSONObject(
            requestBytes(
                "$API/repos/$OWNER/$REPOSITORY/releases/assets/$metaId",
                token,
                "application/octet-stream",
                Stage.FILE,
            ).toString(Charsets.UTF_8),
        )
        if (metadata.optString("packageName") != context.packageName) {
            throw IOException("The release belongs to a different Android app.")
        }
        val latestCode = metadata.getLong("versionCode")
        if (latestCode <= installedVersionCode()) return null
        return PhoneUpdateInfo(
            versionCode = latestCode,
            versionName = metadata.optString("versionName", "New version"),
            apkAssetId = apkId,
            sha256 = metadata.getString("sha256").lowercase(),
            notes = metadata.optString("notes", "").trim(),
            sizeBytes = apkSize,
        )
    }

    /**
     * Downloads and verifies the APK. [onProgress] gets (bytesSoFar, totalBytes) a few times a second;
     * totalBytes is 0 when the size is unknown. Runs on the calling thread, so call it off the main thread.
     */
    fun downloadUpdate(token: String, update: PhoneUpdateInfo, onProgress: (Long, Long) -> Unit = { _, _ -> }): File {
        val directory = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
        val output = File(directory, "Phone-${update.versionCode}.apk")
        val temporary = File(directory, "Phone-update.part")
        // A copy that was already downloaded and verified is reused, so a cancelled install doesn't cost another download.
        if (output.exists() && sha256(output).equals(update.sha256, ignoreCase = true)) return output

        val connection = openFollowing(
            "$API/repos/$OWNER/$REPOSITORY/releases/assets/${update.apkAssetId}",
            token,
            "application/octet-stream",
            Stage.FILE,
        )
        try {
            val length = connection.contentLengthLong
            if (length > MAX_APK_BYTES) throw IOException("APK download is unexpectedly large.")
            val expected = if (length > 0) length else update.sizeBytes
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    var reported = 0L
                    onProgress(0L, expected)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_APK_BYTES) throw IOException("APK download exceeded the size limit.")
                        target.write(buffer, 0, count)
                        if (total - reported >= PROGRESS_STEP_BYTES) {
                            reported = total
                            onProgress(total, expected)
                        }
                    }
                    target.fd.sync()
                    onProgress(total, if (expected > 0) expected else total)
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

    /** Removes downloaded APKs; called once the installed version is up to date. */
    fun clearDownloads() {
        File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
    }

    private fun requestBytes(url: String, token: String, accept: String, stage: Stage): ByteArray {
        val connection = openFollowing(url, token, accept, stage)
        try {
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Opens [startUrl] and follows redirects by hand. GitHub answers an asset request with a redirect to a
     * pre-signed download URL on another host; the token must not be sent there (the storage host rejects it).
     * Returns a connection whose response is already known to be 2xx.
     */
    private fun openFollowing(startUrl: String, token: String, accept: String, stage: Stage): HttpURLConnection {
        var current = URL(startUrl)
        var sendToken = true
        for (hop in 0..MAX_REDIRECTS) {
            val connection = openConnection(current.toString(), if (sendToken) token else null, accept)
            val status = connection.responseCode
            if (status in REDIRECT_CODES) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw IOException("GitHub redirected the download without saying where.")
                val next = URL(current, location)
                if (next.protocol != "https") throw IOException("Refusing a download redirect that isn't HTTPS.")
                if (next.host != current.host) sendToken = false
                current = next
                continue
            }
            if (status !in 200..299) {
                val error = httpError(connection, status, stage)
                connection.disconnect()
                throw error
            }
            return connection
        }
        throw IOException("Too many redirects while contacting GitHub.")
    }

    private fun openConnection(url: String, token: String?, accept: String): HttpURLConnection {
        return (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = false
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", accept)
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            setRequestProperty("User-Agent", "Phone-Android-Updater")
        }
    }

    private fun httpError(connection: HttpURLConnection, status: Int, stage: Stage): IOException {
        val body = runCatching { connection.errorStream?.bufferedReader()?.use { it.readText() } }.getOrNull().orEmpty()
        val github = runCatching { JSONObject(body).optString("message") }.getOrDefault("").trim()
        return IOException(updateErrorMessage(status, stage, github))
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
        /** Must match the FileProvider path in res/xml/update_paths.xml. */
        const val UPDATE_DIR = "updates"
        const val MAX_APK_BYTES = 100L * 1024L * 1024L
        const val PROGRESS_STEP_BYTES = 256L * 1024L
        const val MAX_REDIRECTS = 5
        val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)
    }
}
