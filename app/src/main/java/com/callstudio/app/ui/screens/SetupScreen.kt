@file:OptIn(ExperimentalMaterial3Api::class)

package com.callstudio.app.ui.screens

import android.Manifest
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.navigation.NavController
import com.callstudio.app.container
import com.callstudio.app.diag.CallDiagnostics
import com.callstudio.app.permissions.DndLevel
import com.callstudio.app.permissions.DoNotDisturb
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.notifications.NotificationHelper
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
        val battery = SystemAccess.ignoringBatteryOptimizations(ctx)
        val alertsLoud = ctx.container.notifications.incomingChannelAllowsPopUp()
        val ringerNow = SystemAccess.ringerModeLabel(ctx)
        val dndNow = SystemAccess.doNotDisturbLabel(ctx)
        val dndLevel = DoNotDisturb.read(ctx)
        val overridesDnd = ctx.container.notifications.incomingChannelOverridesDnd()
        val dndAccess = SystemAccess.notificationPolicyAccessGranted(ctx)
        val strictDnd = dndLevel == DndLevel.ALARMS_ONLY || dndLevel == DndLevel.TOTAL_SILENCE
        val dndMaySilence = DoNotDisturb.mayStaySilent(dndLevel, overridesDnd)
        val overApps = SystemAccess.canDrawOverOtherApps(ctx)
        val dndBody = buildString {
            if (strictDnd) {
                append("${dndNow ?: "Do Not Disturb"} is on right now and silences every call, as it does for real calls. Switch it to Priority only, or turn it off, to hear calls. ")
            }
            when {
                overridesDnd -> append("Allowed. Calls ring, vibrate and pop up even when Do Not Disturb is set to Priority only.")
                !dndAccess -> append("Tap Allow and switch CallStudio on, so calls can ring through Do Not Disturb like real calls.")
                else -> append("Tap Allow, then switch on Override Do Not Disturb for Incoming calls, so calls can ring through Do Not Disturb like real calls.")
            }
        }
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
                "Incoming-call alerts",
                if (alertsLoud) "On. Calls pop up on screen and on the lock screen."
                else "Android is showing incoming-call alerts quietly, so they won't pop up on screen or on the lock screen. Open the setting and choose Pop on screen / Urgent.",
                if (alertsLoud) Status.OK else Status.NEEDS_ACTION,
                if (alertsLoud) null else "Open settings",
                { SystemAccess.openChannelSettings(ctx, NotificationHelper.CHANNEL_INCOMING) },
            ),
            SetupItem(
                "Ring through Do Not Disturb",
                dndBody,
                when {
                    strictDnd -> Status.NEEDS_ACTION
                    overridesDnd -> Status.OK
                    dndMaySilence -> Status.NEEDS_ACTION
                    else -> Status.INFO
                },
                if (overridesDnd) null else "Allow",
                {
                    // Step 1: let CallStudio manage Do Not Disturb. Step 2 (only if Android kept an older choice for the
                    // channel): switch on "Override Do Not Disturb" in the call channel's own settings.
                    if (dndAccess) SystemAccess.openChannelSettings(ctx, NotificationHelper.CHANNEL_INCOMING)
                    else SystemAccess.openDndAccessSettings(ctx)
                },
            ),
            SetupItem(
                "Call screen over other apps",
                if (overApps) "Allowed. A ringing call opens its full screen over whatever you are doing, like a real call."
                else "Not allowed. While you use another app, a call shows as a banner you tap instead of opening by itself. Allow \"Display over other apps\" to change that.",
                if (overApps) Status.OK else Status.INFO,
                if (overApps) null else "Allow",
                { SystemAccess.openOverlaySettings(ctx) },
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
                "Works when the app is closed",
                if (battery) "Allowed. Scheduled calls arrive even when CallStudio is closed and the phone is locked."
                else "Battery optimisation is on. Some phones then delay or block calls while the app is closed. Allow it so calls arrive on time.",
                if (battery) Status.OK else Status.NEEDS_ACTION,
                if (battery) null else "Allow background",
                { SystemAccess.openBatteryOptimizationSettings(ctx) },
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
                "CallStudio rings like a real call. Silent: no sound or vibration. Vibrate: vibration only. Ring: your ringtone, plus vibration if your phone vibrates for calls. Do Not Disturb may hide it, as it does real calls.\n\n" +
                    "Right now: the phone is set to ${ringerNow.lowercase()}, " + (dndNow?.let { "$it is on." } ?: "Do Not Disturb is off."),
                if (dndNow != null || ringerNow == "Silent") Status.NEEDS_ACTION else Status.INFO,
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
            CallReportCard(tick)
        }
    }
}

/** What happened the last time a call arrived (alarm, notification, ringing). Helps find out why a call was quiet. */
@Composable
private fun CallReportCard(tick: Int) {
    var refresh by remember { mutableIntStateOf(0) }
    val lines = remember(tick, refresh) { CallDiagnostics.recent(18) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text("Last call report", style = MaterialTheme.typography.titleMedium)
            Text(
                if (lines.isEmpty()) "Nothing yet. After a call arrives, what happened shows here."
                else "Newest first. Shows what Android allowed when the call arrived.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = 6.dp))
            }
            Row {
                TextButton(onClick = { refresh++ }) { Text("Refresh") }
                if (lines.isNotEmpty()) TextButton(onClick = { CallDiagnostics.clear(); refresh++ }) { Text("Clear") }
            }
        }
    }
}
