package com.callstudio.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** What the dashboard banner and the Settings update section show. */
sealed interface UpdateUiState {
    /** No GitHub token saved yet, so the private release can't be read. */
    data object NotConfigured : UpdateUiState
    data object Idle : UpdateUiState
    data object Checking : UpdateUiState
    data object UpToDate : UpdateUiState
    /** The last check could not finish (offline, bad token, ...). Only Settings shows this. */
    data class CheckFailed(val message: String) : UpdateUiState
    /** A newer build is ready to download. [error] is set when the last download attempt failed. */
    data class Available(val info: PhoneUpdateInfo, val error: String? = null) : UpdateUiState
    data class Downloading(val info: PhoneUpdateInfo, val downloaded: Long, val total: Long) : UpdateUiState
    /** Downloaded and verified. Android's installer has been opened or is waiting for "install unknown apps". */
    data class ReadyToInstall(val info: PhoneUpdateInfo, val apk: File, val message: String? = null) : UpdateUiState
}

/**
 * Lives for the whole process (owned by AppContainer) so a download keeps going if the user leaves the screen.
 * Installing replaces the app in place: settings, callers, schedules and permissions all stay.
 */
class UpdateController(private val appContext: Context, private val scope: CoroutineScope) {
    private val tokens = GitHubTokenStore(appContext)
    private val manager = PhoneUpdateManager(appContext)

    private val _state = MutableStateFlow<UpdateUiState>(UpdateUiState.Idle)
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    private var busy = false
    private var lastCheckAt = 0L

    /** Set while we wait for the user to allow "install unknown apps" for this app. */
    private var waitingForInstallPermission: File? = null

    val installedVersionName: String get() = manager.installedVersionName()
    val installedVersionCode: Long get() = manager.installedVersionCode()

    /** Checks quietly when the app opens or returns to the front, at most every few minutes. */
    fun checkIfStale() {
        if (SystemClock.elapsedRealtime() - lastCheckAt < MIN_CHECK_INTERVAL_MS && lastCheckAt != 0L) return
        check()
    }

    fun check() {
        if (busy) return
        busy = true
        scope.launch {
            val before = _state.value
            try {
                val token = withContext(Dispatchers.IO) { tokens.readToken() }
                if (token.isNullOrBlank()) {
                    _state.value = UpdateUiState.NotConfigured
                    return@launch
                }
                // Don't flash "Checking" over a banner the user may be about to tap.
                if (before !is UpdateUiState.Available && before !is UpdateUiState.ReadyToInstall) {
                    _state.value = UpdateUiState.Checking
                }
                val info = withContext(Dispatchers.IO) { manager.checkForUpdate(token) }
                lastCheckAt = SystemClock.elapsedRealtime()
                if (info == null) {
                    withContext(Dispatchers.IO) { manager.clearDownloads() }
                    _state.value = UpdateUiState.UpToDate
                } else {
                    val keep = (before as? UpdateUiState.ReadyToInstall)?.takeIf { it.info.versionCode == info.versionCode }
                    _state.value = keep ?: UpdateUiState.Available(info)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                // A failed background check must not hide an update we already know about.
                _state.value = if (before is UpdateUiState.Available || before is UpdateUiState.ReadyToInstall) {
                    before
                } else {
                    UpdateUiState.CheckFailed(e.message ?: "Could not check for updates.")
                }
            } finally {
                busy = false
            }
        }
    }

    fun downloadAndInstall() {
        val info = when (val s = _state.value) {
            is UpdateUiState.Available -> s.info
            is UpdateUiState.ReadyToInstall -> {
                install(s.info, s.apk)
                return
            }
            else -> return
        }
        if (busy) return
        busy = true
        scope.launch {
            _state.value = UpdateUiState.Downloading(info, 0L, info.sizeBytes)
            try {
                val token = withContext(Dispatchers.IO) { tokens.readToken() }
                    ?: throw IllegalStateException("Save your GitHub token again in Settings.")
                val apk = withContext(Dispatchers.IO) {
                    manager.downloadUpdate(token, info) { done, total ->
                        _state.value = UpdateUiState.Downloading(info, done, total)
                    }
                }
                install(info, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Update download failed", e)
                _state.value = UpdateUiState.Available(info, e.message ?: "Update download failed.")
            } finally {
                busy = false
            }
        }
    }

    /** Call from the activity's onResume: finishes the install once the user has allowed unknown-app installs. */
    fun onAppResumed() {
        val pending = waitingForInstallPermission
        if (pending != null && canInstallPackages()) {
            waitingForInstallPermission = null
            val ready = _state.value as? UpdateUiState.ReadyToInstall ?: return
            install(ready.info, pending)
        }
        checkIfStale()
    }

    fun saveToken(token: String) {
        tokens.saveToken(token)
        check()
    }

    fun clearToken() {
        tokens.clear()
        _state.value = UpdateUiState.NotConfigured
    }

    fun hasToken(): Boolean = tokens.hasToken()

    private fun install(info: PhoneUpdateInfo, apk: File) {
        if (!apk.exists()) {
            _state.value = UpdateUiState.Available(info, "The downloaded file went missing. Download again.")
            return
        }
        if (!canInstallPackages()) {
            waitingForInstallPermission = apk
            _state.value = UpdateUiState.ReadyToInstall(
                info, apk, "Allow CallStudio to install apps on the next screen, then come back here.",
            )
            startSafely(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${appContext.packageName}")),
            ) { _state.value = UpdateUiState.ReadyToInstall(info, apk, it) }
            return
        }
        _state.value = UpdateUiState.ReadyToInstall(info, apk, "Tap Install on Android's screen. Your settings and data stay.")
        val uri = try {
            FileProvider.getUriForFile(appContext, "${appContext.packageName}.updateprovider", apk)
        } catch (e: IllegalArgumentException) {
            _state.value = UpdateUiState.ReadyToInstall(info, apk, "Could not open the downloaded file.")
            return
        }
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
        startSafely(intent) { _state.value = UpdateUiState.ReadyToInstall(info, apk, it) }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || appContext.packageManager.canRequestPackageInstalls()

    private fun startSafely(intent: Intent, onFailure: (String) -> Unit) {
        try {
            appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Log.w(TAG, "Could not open the installer screen", e)
            onFailure(e.message ?: "Could not open Android's installer.")
        }
    }

    private companion object {
        const val TAG = "PhoneUpdater"
        const val MIN_CHECK_INTERVAL_MS = 5 * 60 * 1000L
    }
}
