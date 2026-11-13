package com.callstudio.app.notifications

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Color as AndroidColor
import android.util.Log
import android.widget.RemoteViews
import android.media.AudioAttributes
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.callstudio.app.CallActivity
import com.callstudio.app.R
import com.callstudio.app.data.PhotoStore
import com.callstudio.app.domain.PhoneNumbers
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.preferences.NotificationBadgeStyle
import com.callstudio.app.preferences.SettingsStore
import com.callstudio.app.session.SessionState

class NotificationHelper(private val context: Context, private val photos: PhotoStore) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
        // A channel's importance and sound can't be changed by the app once it exists, and an older build (or a
        // switch flipped in Android settings) may have left the call channels muted. New ids start clean.
        LEGACY_CHANNELS.forEach { runCatching { system.deleteNotificationChannel(it) } }
        val ring = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        // Sound and vibration come from the in-app ringtone player; this channel only shows the call.
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shows the call screen when a scheduled call arrives"
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        // Used only if the ringing service can't start; the system then plays the default ringtone.
        val backup = NotificationChannel(CHANNEL_BACKUP, "Incoming calls (backup alert)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Used when ringing can't run in the background"
            setSound(Settings.System.DEFAULT_RINGTONE_URI, ring)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 700, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Ongoing call", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shows the call in progress with hang up and mute"
            setShowBadge(false)
        }
        // Like the phone's own "missed call" notification: normal priority, default notification sound.
        val missed = NotificationChannel(CHANNEL_MISSED, "Missed calls", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Shows a missed-call notification when a call rings out"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(true)
        }
        system.createNotificationChannels(listOf(incoming, backup, ongoing, missed))
    }

    /** Same as a real phone: a call that rang out leaves a "Missed call" notification that opens the app. */
    @SuppressLint("MissingPermission")
    fun postMissed(eventId: Long, name: String, photoFile: String?) {
        if (!manager.areNotificationsEnabled()) return
        val open = PendingIntent.getActivity(
            context,
            RequestCodes.forEvent(eventId, RequestCodes.MISSED),
            Intent(context, com.callstudio.app.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = name.ifBlank { "Unknown" }
        val b = NotificationCompat.Builder(context, CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Missed call")
            .setContentText(title)
            .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
        avatar(photoFile, readBadgeStyle())?.let { b.setLargeIcon(it) }
        manager.notify(MISSED_ID_BASE + (eventId and 0xFFFL).toInt(), b.build())
    }

    fun buildIncoming(s: SessionState, backup: Boolean): Notification {
        val open = activityIntent(s.eventId, answer = false)
        val answer = activityIntent(s.eventId, answer = true)
        val decline = broadcastIntent(s.eventId, ACTION_DECLINE, RequestCodes.DECLINE)
        val badgeStyle = readBadgeStyle()
        val callerPortrait = avatar(s.callerPhotoFile, badgeStyle)
        val b = NotificationCompat.Builder(context, if (backup) CHANNEL_BACKUP else CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.displayName)
            .setContentText("Incoming call")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Incoming call"))
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            // The call notification is posted first and then adopted by the ringing service; the second post must
            // not pop up again. The backup version is allowed to alert: that is its whole job.
            .setOnlyAlertOnce(!backup)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(0, "Decline", decline)
            .addAction(0, "Answer", answer)

        // Do Not Disturb decides "calls from contacts only" using the caller's number, exactly as it does for a real
        // call. Without this the notification has no caller at all and is treated as a stranger.
        val handle = PhoneNumbers.dialable(s.callerPhone)
        if (handle.isNotEmpty()) {
            b.addPerson(Person.Builder().setName(s.displayName.ifBlank { handle }).setKey(handle).setUri("tel:$handle").build())
        }
        if (callerPortrait != null) b.setLargeIcon(callerPortrait)
        // The full-screen activity remains responsible for the Truecaller-style incoming UI.
        // A standard call-category notification avoids Android's extra app-icon badge on the photo.
        if (SystemAccess.fullScreenAllowed(context)) b.setFullScreenIntent(open, true)
        val n = b.build()
        if (backup) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }

    fun buildOngoing(s: SessionState): Notification {
        val hangUp = broadcastIntent(s.eventId, ACTION_END, RequestCodes.HANG_UP)
        val mute = broadcastIntent(s.eventId, ACTION_TOGGLE_MUTE, RequestCodes.MUTE)
        val badgeStyle = readBadgeStyle()
        // Do not use CallStyle here: on some Android skins it forcibly overlays the
        // system/Android app badge on the caller portrait. This custom card keeps the
        // caller photo clean and lets the user choose the optional online/app marker.
        val portrait = avatar(s.callerPhotoFile, badgeStyle)
        val remote = RemoteViews(context.packageName, R.layout.notification_ongoing).apply {
            setTextViewText(R.id.call_title, s.displayName.ifBlank { "Unknown" })
            setTextViewText(R.id.call_status, if (s.recording.isActive) "Ongoing call · Recording" else "Ongoing call")
            setTextViewText(R.id.action_mute, if (s.muted) "Unmute" else "Mute")
            if (portrait != null) setImageViewBitmap(R.id.call_avatar, portrait)
            setOnClickPendingIntent(R.id.ongoing_root, activityIntent(s.eventId, answer = false))
            setOnClickPendingIntent(R.id.action_hangup, hangUp)
            setOnClickPendingIntent(R.id.action_mute, mute)
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.displayName.ifBlank { "Ongoing call" })
            .setContentText("Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityIntent(s.eventId, answer = false))
            .setCustomContentView(remote)
            .setCustomBigContentView(remote)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .build()
    }

    private fun readBadgeStyle(): NotificationBadgeStyle = runCatching {
        val raw = context.getSharedPreferences(SettingsStore.NOTIFICATION_PREF_FILE, Context.MODE_PRIVATE)
            .getString(SettingsStore.NOTIFICATION_PREF_KEY, NotificationBadgeStyle.APP_LOGO.name)
        NotificationBadgeStyle.valueOf(raw ?: NotificationBadgeStyle.APP_LOGO.name)
    }.getOrDefault(NotificationBadgeStyle.APP_LOGO)

    /** Placeholder used only to satisfy the foreground-service contract when the call is already over. */
    fun buildEnded(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Call ended")
            .setOngoing(false)
            .build()

    /**
     * Posts the incoming-call notification straight away, without waiting for the ringing service to start.
     * The service later adopts it as its foreground notification (same id), so nothing is shown twice.
     * @return false when Android would not show it (notifications off or posting failed).
     */
    @SuppressLint("MissingPermission")
    fun postIncoming(s: SessionState, backup: Boolean = false): Boolean {
        if (!manager.areNotificationsEnabled()) return false
        return try {
            manager.notify(NOTIFICATION_ID, buildIncoming(s, backup))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Could not post the incoming call notification", e)
            false
        }
    }

    /** Used when the app's own ringtone can't run: the phone's notification sound rings instead, until answered. */
    fun postBackupIncoming(s: SessionState): Boolean = postIncoming(s, backup = true)

    fun notificationsEnabled(): Boolean = manager.areNotificationsEnabled()

    /** Importance Android currently applies to the call channel (the user can lower it in settings), or null if unknown. */
    private fun incomingChannelImportance(): Int? = runCatching {
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_INCOMING)?.importance
    }.getOrNull()

    /** True when Android lets the call notification pop up on screen and on the lock screen. */
    fun incomingChannelAllowsPopUp(): Boolean {
        val importance = incomingChannelImportance() ?: return true
        return importance >= NotificationManager.IMPORTANCE_HIGH
    }

    /** Short word for the call report: how Android currently treats the call notification channel. */
    fun incomingChannelState(): String = when (val importance = incomingChannelImportance()) {
        null -> "unknown"
        NotificationManager.IMPORTANCE_NONE -> "OFF"
        NotificationManager.IMPORTANCE_MIN -> "min"
        NotificationManager.IMPORTANCE_LOW -> "low"
        NotificationManager.IMPORTANCE_DEFAULT -> "default"
        else -> if (importance >= NotificationManager.IMPORTANCE_HIGH) "high" else importance.toString()
    }

    fun cancelAll() = manager.cancel(NOTIFICATION_ID)

    private fun activityIntent(eventId: Long, answer: Boolean): PendingIntent {
        val purpose = if (answer) RequestCodes.ANSWER else RequestCodes.OPEN
        return PendingIntent.getActivity(
            context,
            RequestCodes.forEvent(eventId, purpose),
            CallActivity.intent(context, eventId, answer),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun broadcastIntent(eventId: Long, action: String, purpose: Int): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(action)
            .setData(Uri.parse("callstudio://call/$eventId/$purpose"))
            .putExtra(EXTRA_EVENT_ID, eventId)
        return PendingIntent.getBroadcast(
            context,
            RequestCodes.forEvent(eventId, purpose),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Makes a true circular contact portrait and composites the user's chosen badge into it. */
    private fun avatar(name: String?, badgeStyle: NotificationBadgeStyle, useSystemAppBadge: Boolean = false): Bitmap? {
        val file = photos.file(name) ?: return null
        return try {
            val source = BitmapFactory.decodeFile(file.path) ?: return null
            val side = minOf(source.width, source.height)
            val crop = Bitmap.createBitmap(
                source,
                (source.width - side) / 2,
                (source.height - side) / 2,
                side,
                side,
            )
            val size = 192
            val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(output)
            val portraitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            val circle = Path().apply { addCircle(size / 2f, size / 2f, size / 2f - 1f, Path.Direction.CW) }
            canvas.save()
            canvas.clipPath(circle)
            canvas.drawBitmap(crop, null, RectF(0f, 0f, size.toFloat(), size.toFloat()), portraitPaint)
            canvas.restore()
            crop.recycle()
            if (source !== crop && !source.isRecycled) source.recycle()

            val cx = size * 0.79f
            val cy = size * 0.79f
            val radius = size * 0.17f
            val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
            badgePaint.color = AndroidColor.WHITE
            canvas.drawCircle(cx, cy, radius + 4f, badgePaint)
            when (badgeStyle) {
                NotificationBadgeStyle.ONLINE_DOT -> {
                    badgePaint.color = AndroidColor.rgb(46, 204, 96)
                    canvas.drawCircle(cx, cy, radius, badgePaint)
                }
                NotificationBadgeStyle.APP_LOGO -> if (!useSystemAppBadge) {
                    badgePaint.color = AndroidColor.rgb(26, 115, 232)
                    canvas.drawCircle(cx, cy, radius, badgePaint)
                    val phone = Path().apply {
                        moveTo(cx - radius * 0.45f, cy - radius * 0.48f)
                        cubicTo(cx - radius * 0.62f, cy - radius * 0.35f, cx - radius * 0.30f, cy + radius * 0.37f, cx + radius * 0.43f, cy + radius * 0.48f)
                    }
                    badgePaint.color = AndroidColor.WHITE
                    badgePaint.style = Paint.Style.STROKE
                    badgePaint.strokeWidth = 7f
                    badgePaint.strokeCap = Paint.Cap.ROUND
                    badgePaint.strokeJoin = Paint.Join.ROUND
                    canvas.drawPath(phone, badgePaint)
                }
            }
            output
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "incoming_call_v2"
        const val CHANNEL_BACKUP = "incoming_call_backup_v2"
        private val LEGACY_CHANNELS = listOf("incoming_call", "incoming_call_backup")
        private const val TAG = "NotificationHelper"
        const val CHANNEL_ONGOING = "ongoing_call"
        const val CHANNEL_MISSED = "missed_call"
        const val NOTIFICATION_ID = 4101
        private const val MISSED_ID_BASE = 4200
        const val EXTRA_EVENT_ID = "event_id"
        const val ACTION_DECLINE = "com.callstudio.app.action.DECLINE"
        const val ACTION_END = "com.callstudio.app.action.END"
        const val ACTION_TOGGLE_MUTE = "com.callstudio.app.action.TOGGLE_MUTE"
    }
}
