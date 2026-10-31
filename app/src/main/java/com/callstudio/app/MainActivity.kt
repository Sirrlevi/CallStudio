package com.callstudio.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.callstudio.app.ui.AppNav
import com.callstudio.app.ui.theme.CallStudioTheme
import com.callstudio.app.update.GitHubTokenStore
import com.callstudio.app.update.PhoneUpdateInfo
import com.callstudio.app.update.PhoneUpdateManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private val updatePrompt = mutableStateOf<PhoneUpdateInfo?>(null)
    private val updateBusy = mutableStateOf(false)
    private val updateError = mutableStateOf<String?>(null)
    private val checkingUpdates = mutableStateOf(false)
    private var pendingInstallFile: File? = null
    private lateinit var tokenStore: GitHubTokenStore
    private lateinit var updateManager: PhoneUpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tokenStore = GitHubTokenStore(this)
        updateManager = PhoneUpdateManager(this)

        setContent {
            CallStudioTheme {
                Box(Modifier.fillMaxSize()) {
                    AppNav()
                    val available = updatePrompt.value
                    if (available != null) {
                        AlertDialog(
                            onDismissRequest = { if (!updateBusy.value) updatePrompt.value = null },
                            title = { Text("Update available") },
                            text = {
                                Column {
                                    Text("Phone ${available.versionName} is ready. Download and install the latest version?")
                                    if (updateBusy.value) {
                                        LinearProgressIndicator(Modifier.padding(top = androidx.compose.ui.unit.dp(12)))
                                        Text("Downloading update…", modifier = Modifier.padding(top = androidx.compose.ui.unit.dp(8)))
                                    }
                                    updateError.value?.let {
                                        Text(it, modifier = Modifier.padding(top = androidx.compose.ui.unit.dp(8)))
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    enabled = !updateBusy.value,
                                    onClick = { downloadAndInstall(available) },
                                ) { Text(if (updateBusy.value) "Downloading…" else "Download & install") }
                            },
                            dismissButton = {
                                TextButton(
                                    enabled = !updateBusy.value,
                                    onClick = { updatePrompt.value = null },
                                ) { Text("Later") }
                            },
                        )
                    }
                }
            }
        }

        checkForUpdates(showNoUpdateMessage = false)
    }

    /** Called by Settings after the user saves their one-time private-repo token. */
    fun checkForUpdatesNow() {
        checkForUpdates(showNoUpdateMessage = true)
    }

    private fun checkForUpdates(showNoUpdateMessage: Boolean) {
        if (checkingUpdates.value) return
        lifecycleScope.launch {
            checkingUpdates.value = true
            try {
                delay(700)
                val token = tokenStore.readToken()
                if (token.isNullOrBlank()) {
                    if (showNoUpdateMessage) {
                        Toast.makeText(this@MainActivity, "Save a GitHub token in Settings to enable updates.", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                val update = withContext(Dispatchers.IO) { updateManager.checkForUpdate(token) }
                if (update != null) {
                    updateError.value = null
                    updatePrompt.value = update
                } else if (showNoUpdateMessage) {
                    Toast.makeText(this@MainActivity, "Phone is already up to date.", Toast.LENGTH_SHORT).show()
                }
            } catch (error: Exception) {
                Log.w("PhoneUpdater", "Update check failed", error)
                if (showNoUpdateMessage) {
                    Toast.makeText(
                        this@MainActivity,
                        error.message ?: "Could not check for updates.",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            } finally {
                checkingUpdates.value = false
            }
        }
    }

    private fun downloadAndInstall(update: PhoneUpdateInfo) {
        if (updateBusy.value) return
        lifecycleScope.launch {
            updateBusy.value = true
            updateError.value = null
            try {
                val token = tokenStore.readToken()
                    ?: throw IllegalStateException("Save your GitHub token again in Settings.")
                val apk = withContext(Dispatchers.IO) { updateManager.downloadUpdate(token, update) }
                updateBusy.value = false
                requestPackageInstall(apk)
            } catch (error: Exception) {
                updateBusy.value = false
                updateError.value = error.message ?: "Update download failed."
            }
        }
    }

    private fun requestPackageInstall(apk: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !packageManager.canRequestPackageInstalls()) {
            pendingInstallFile = apk
            updateError.value = "Allow Phone to install apps on the next screen, then return here."
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:$packageName"),
                ),
            )
            return
        }
        launchPackageInstaller(apk)
    }

    override fun onResume() {
        super.onResume()
        val pending = pendingInstallFile
        if (pending != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.canRequestPackageInstalls()
        ) {
            pendingInstallFile = null
            launchPackageInstaller(pending)
        }
    }

    private fun launchPackageInstaller(apk: File) {
        runCatching {
            val uri = FileProvider.getUriForFile(this, "$packageName.updateprovider", apk)
            val install = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                data = uri
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
            }
            startActivity(install)
        }.onFailure {
            updateError.value = it.message ?: "Could not open Android's package installer."
        }
    }
}
