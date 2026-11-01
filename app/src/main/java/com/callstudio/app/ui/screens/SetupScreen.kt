@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.ui.components.AppTopBar
import com.callstudio.app.ui.components.OnResume

private enum class Status { OK, NEEDS_ACTION, INFO }

private data class SetupItem(
    val title: String,
    val body: String,
    val status: Status,
    val actionLabel: String? = null,
    val onAction: (() -> Unit)? = null,
)

@Composable
fun SetupScreen(nav: NavController) {
    val ctx = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    OnResume { tick++ }

    val notificationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val activity = ctx as? Activity
        val blocked = activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        if (!granted && blocked) SystemAccess.openNotificationSettings(ctx)
        tick++
    }

    val items = remember(tick) {
        val notificationsOn = SystemAccess.notificationsEnabled(ctx)
        val fullScreen = SystemAccess.fullScreenAllowed(ctx)
        val exact = SystemAccess.exactAlarmsAllowed(ctx)
        listOf(
            SetupItem(
                "Notifications",
                if (notificationsOn) "On. Calls can be shown." else "Off. Without notifications a scheduled call can't appear.",
                if (notificationsOn) Status.OK else Status.NEEDS_ACTION,
                if (notificationsOn) null else "Allow notifications",
                {
                    if (SystemAccess.needsNotificationPermission(ctx)) notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else SystemAccess.openNotificationSettings(ctx)
                },
            ),
            SetupItem(
                "Full-screen call screen",
                if (fullScreen) "Allowed. Calls can take over the screen, even when locked."
                else "Not allowed. Calls will show as a banner you tap instead of filling the screen.",
                if (fullScreen) Status.OK else Status.NEEDS_ACTION,
                if (fullScreen) null else "Open settings",
                { SystemAccess.openFullScreenSettings(ctx) },
            ),
            SetupItem(
                "Exact timing",
                if (exact) "Allowed. Calls ring at the time you pick."
                else "Not allowed. Android may deliver calls a few minutes late.",
                if (exact) Status.OK else Status.NEEDS_ACTION,
                if (exact) null else "Open settings",
                { SystemAccess.openExactAlarmSettings(ctx) },
            ),
            SetupItem(
                "Microphone",
                if (SystemAccess.micGranted(ctx)) "Allowed. Recording starts only when you tap Record."
                else "Asked the first time you tap Record during a call. Nothing records on its own.",
                Status.INFO,
            ),
            SetupItem(
                "Contacts",
                if (SystemAccess.contactsGranted(ctx)) "Allowed. Used only to copy the photo of the contact you choose."
                else "Asked when you tap Choose from contacts. It is used only to copy the chosen contact's photo; without it you can still pick a photo yourself.",
                Status.INFO,
            ),
            SetupItem(
                "Internet (Outgoing tab only)",
                "Fake incoming calls never use the internet. Only the Outgoing tab does, and only when you start a call there: it talks to your Twilio account, and to your Cloudinary account if you upload clips from this phone.",
                Status.INFO,
            ),
            SetupItem(
                "Silent mode and Do Not Disturb",
                "CallStudio follows them. In silent mode a call appears without sound, and Do Not Disturb may hide it.",
                Status.INFO,
            ),
            SetupItem(
                "After a force stop",
                "If you force-stop CallStudio, Android cancels its pending alarms. Open the app once and scheduled calls are set up again.",
                Status.INFO,
            ),
        )
    }

    Scaffold(topBar = { AppTopBar("Permissions and setup", onBack = { nav.popBackStack() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
            items.forEach { item ->
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    ListItem(
                        headlineContent = { Text(item.title) },
                        supportingContent = {
                            Column {
                                Text(item.body)
                                if (item.actionLabel != null && item.onAction != null) {
                                    Button(onClick = item.onAction, modifier = Modifier.padding(top = 8.dp)) { Text(item.actionLabel) }
                                }
                            }
                        },
                        leadingContent = {
                            when (item.status) {
                                Status.OK -> Icon(Icons.Filled.CheckCircle, contentDescription = "Ready", tint = MaterialTheme.colorScheme.primary)
                                Status.NEEDS_ACTION -> Icon(Icons.Filled.Warning, contentDescription = "Needs attention", tint = MaterialTheme.colorScheme.error)
                                Status.INFO -> Icon(Icons.Filled.Info, contentDescription = "Good to know", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                }
            }
        }
    }
}
