package com.callstudio.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.callstudio.app.container
import com.callstudio.app.update.PhoneUpdateInfo
import com.callstudio.app.update.UpdateUiState

/**
 * Dashboard card for the in-app updater: "update available" with a Download button, live download progress,
 * and "install now". Shows nothing when the app is up to date.
 */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val updates = LocalContext.current.container.updates
    val state by updates.state.collectAsStateWithLifecycle()

    when (val s = state) {
        is UpdateUiState.Available -> BannerCard(modifier) {
            BannerHeader("Update available", "Version ${s.info.versionName}${sizeSuffix(s.info)}")
            ChangeNotes(s.info)
            s.error?.let { ErrorLine(it) }
            Button(onClick = { updates.downloadAndInstall() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text(if (s.error == null) "Download and install" else "Try again")
            }
        }
        is UpdateUiState.Downloading -> BannerCard(modifier) {
            BannerHeader("Downloading update", "Version ${s.info.versionName}")
            DownloadProgress(s.downloaded, s.total)
        }
        is UpdateUiState.ReadyToInstall -> BannerCard(modifier) {
            BannerHeader("Ready to install", "Version ${s.info.versionName}")
            s.message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            }
            Button(onClick = { updates.downloadAndInstall() }, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                Text("Install now")
            }
        }
        else -> Unit
    }
}

@Composable
private fun BannerCard(modifier: Modifier, content: @Composable () -> Unit) {
    Card(modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun BannerHeader(title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.SystemUpdate, contentDescription = null)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ChangeNotes(info: PhoneUpdateInfo) {
    if (info.notes.isBlank()) return
    Text(
        info.notes,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 5,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ErrorLine(message: String) {
    Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
}

/** Progress bar plus "42% · 8.1 of 19.3 MB". Falls back to an endless bar when the size is unknown. */
@Composable
fun DownloadProgress(downloaded: Long, total: Long, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (total > 0L) {
            val fraction = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            Text("${(fraction * 100).toInt()}% · ${megabytes(downloaded)} of ${megabytes(total)}", style = MaterialTheme.typography.bodySmall)
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            Text(megabytes(downloaded), style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun sizeSuffix(info: PhoneUpdateInfo): String = if (info.sizeBytes > 0L) " · ${megabytes(info.sizeBytes)}" else ""

private fun megabytes(bytes: Long): String = "%.1f MB".format(bytes / (1024.0 * 1024.0))

/** Same text used by the Settings section so both places describe a build the same way. */
fun describeBuild(versionName: String, versionCode: Long): String = "$versionName (build $versionCode)"
