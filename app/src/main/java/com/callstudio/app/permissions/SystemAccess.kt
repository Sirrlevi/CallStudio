package com.callstudio.app.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.callstudio.app.container

/** Status checks and official settings shortcuts for the access the app can use. */
object SystemAccess {
    fun notificationsEnabled(ctx: Context): Boolean = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    fun needsNotificationPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED

    fun fullScreenAllowed(ctx: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ctx.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        } else true

    fun exactAlarmsAllowed(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || ctx.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun ignoringBatteryOptimizations(ctx: Context): Boolean =
        ctx.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(ctx.packageName) ?: true

    fun contactsGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun micGranted(ctx: Context): Boolean =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** What a real call would do right now, before Do Not Disturb: "Ring", "Vibrate" or "Silent". */
    fun ringerModeLabel(ctx: Context): String =
        when (runCatching { ctx.getSystemService(AudioManager::class.java).ringerMode }.getOrDefault(AudioManager.RINGER_MODE_NORMAL)) {
            AudioManager.RINGER_MODE_SILENT -> "Silent"
            AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"
            else -> "Ring"
        }

    /** Null when Do Not Disturb is off, otherwise a short description of how strict it is. */
    fun doNotDisturbLabel(ctx: Context): String? =
        when (runCatching { ctx.getSystemService(NotificationManager::class.java).currentInterruptionFilter }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_UNKNOWN)) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "Do Not Disturb (priority only)"
            NotificationManager.INTERRUPTION_FILTER_ALARMS -> "Do Not Disturb (alarms only)"
            NotificationManager.INTERRUPTION_FILTER_NONE -> "Do Not Disturb (total silence)"
            else -> null
        }

    /** Why a call may stay quiet right now (shown when scheduling), or null when nothing is in the way. */
    fun quietHint(ctx: Context): String? {
        val level = DoNotDisturb.read(ctx)
        val overrides = runCatching { ctx.container.notifications.incomingChannelOverridesDnd() }.getOrDefault(false)
        return when {
            DoNotDisturb.mayStaySilent(level, overrides) -> when (level) {
                DndLevel.ALARMS_ONLY, DndLevel.TOTAL_SILENCE ->
                    "${doNotDisturbLabel(ctx) ?: "Do Not Disturb"} is on, so the call will stay silent, like a real call."
                else ->
                    "Do Not Disturb is on, so this call may stay silent. Allow it in Setup (Ring through Do Not Disturb)."
            }
            ringerModeLabel(ctx) == "Silent" -> "The phone is on silent, so it won't ring."
            else -> null
        }
    }

    /** True when CallStudio may change Do Not Disturb settings, which is what lets its calls ring through it. */
    fun notificationPolicyAccessGranted(ctx: Context): Boolean =
        runCatching { ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted }.getOrDefault(false)

    /** True when CallStudio may open its call screen over other apps ("Display over other apps"). */
    fun canDrawOverOtherApps(ctx: Context): Boolean = runCatching { Settings.canDrawOverlays(ctx) }.getOrDefault(false)

    /** Opens CallStudio's page of the "Display over other apps" settings. */
    fun openOverlaySettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}")),
    )

    /** Opens the page where the user lets CallStudio change Do Not Disturb (needed to ring through it). */
    fun openDndAccessSettings(ctx: Context) {
        val detail = Intent("android.settings.NOTIFICATION_POLICY_ACCESS_DETAIL_SETTINGS", Uri.parse("package:${ctx.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(detail)
        } catch (e: Exception) {
            // Older phones only have the list of all apps.
            start(ctx, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    fun openChannelSettings(ctx: Context, channelId: String) = start(
        ctx,
        Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            .putExtra(Settings.EXTRA_CHANNEL_ID, channelId),
    )

    fun openNotificationSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName),
    )

    fun openFullScreenSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            start(ctx, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${ctx.packageName}")))
        } else openNotificationSettings(ctx)
    }

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            start(ctx, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${ctx.packageName}")))
        } else openAppSettings(ctx)
    }

    /** Asks Android to stop battery-optimising CallStudio; falls back to the general list if the direct dialog is missing. */
    @android.annotation.SuppressLint("BatteryLife")
    fun openBatteryOptimizationSettings(ctx: Context) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (e: ActivityNotFoundException) {
            start(ctx, Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    fun openAppSettings(ctx: Context) = start(
        ctx,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}")),
    )

    private fun start(ctx: Context, intent: Intent) {
        try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            if (intent.action != Settings.ACTION_APPLICATION_DETAILS_SETTINGS) openAppSettings(ctx)
        }
    }
}
