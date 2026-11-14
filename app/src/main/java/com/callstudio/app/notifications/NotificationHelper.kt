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
        // A channel's importance, sound and "override Do Not Disturb" can't be changed by the app once the user has touched
        // it, and an older build (or a switch flipped in Android settings) may have left the call channels muted. New ids
        // start clean, so the app can still switch them on once Do Not Disturb access is allowed.
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
            // Honoured once the user lets CallStudio manage Do Not Disturb (or flips "Override Do Not Disturb" for this
            // channel in Android settings). Without it Android just ignores the request.
            setBypassDnd(true)
        }
        // Used only if the ringing service can't start; the system then plays the default ringtone.
        val backup = NotificationChannel(CHANNEL_BACKUP, "Incoming calls (backup alert)", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Used when ringing can't run in the background"
            setSound(Settings.System.DEFAULT_RINGTONE_URI, ring)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 700, 700, 700)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setShowBadge(false)
            setBypassDnd(true)
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
        avatar(photoFile, greenDot = false)?.let { b.setLargeIcon(it) }
        manager.notify(MISSED_ID_BASE + (eventId and 0xFFFL).toInt(), b.build())
    }

    /**
     * The incoming-call notification. Whenever Android allows it (a foreground-service notification, or one with a
     * full-screen intent) it uses Android's own call style: the card a real call gets, with the caller's photo, the
     * app's real icon on it, and big Answer / Decline buttons. Otherwise it is an ordinary notification with the
     * same two buttons.
     */
    fun buildIncoming(s: SessionState, backup: Boolean, foreground: Boolean = false): Notification {
        val open = activityIntent(s.eventId, answer = false)
        val answer = activityIntent(s.eventId, answer = true)
        val decline = broadcastIntent(s.eventId, ACTION_DECLINE, RequestCodes.DECLINE)
        val badge = readPhotoBadge()
        val fullScreen = SystemAccess.fullScreenAllowed(context)
        val callStyle = foreground || fullScreen
        val portrait = avatar(s.callerPhotoFile, greenDot = badge == PhotoBadge.GREEN_DOT)
        val person = callerPerson(s, portrait)
        val b = NotificationCompat.Builder(context, if (backup) CHANNEL_BACKUP else CHANNEL_INCOMING)
            .setSmallIcon(smallIcon(badge, callStyle))
            .setContentTitle(s.displayName.ifBlank { "Unknown" })
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(CALL_COLOR)
            .setOngoing(true)
            .setAutoCancel(false)
            // The call notification is posted first and then adopted by the ringing service; the second post must
            // not pop up again. The backup version is allowed to alert: that is its whole job.
            .setOnlyAlertOnce(!backup)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Do Not Disturb decides "calls from contacts only" using the caller's number, exactly as for a real call.
            .addPerson(person)
        if (callStyle) {
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
        } else {
            b.addAction(0, "Decline", decline)
            b.addAction(0, "Answer", answer)
            portrait?.let { b.setLargeIcon(it) }
        }
        // The full-screen activity remains responsible for the Truecaller-style incoming UI.
        if (fullScreen) b.setFullScreenIntent(open, true)
        val n = b.build()
        if (backup) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }

    /**
     * The call-in-progress notification: Android's own call style, like the phone's real call card. It shows the
     * caller's photo with the app's real icon on it, a running call timer, and Hang Up and Mute buttons.
     */
    fun buildOngoing(s: SessionState): Notification {
        val hangUp = broadcastIntent(s.eventId, ACTION_END, RequestCodes.HANG_UP)
        val mute = broadcastIntent(s.eventId, ACTION_TOGGLE_MUTE, RequestCodes.MUTE)
        val badge = readPhotoBadge()
        val person = callerPerson(s, avatar(s.callerPhotoFile, greenDot = badge == PhotoBadge.GREEN_DOT))
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(smallIcon(badge, callStyle = true))
            .setContentTitle(s.displayName.ifBlank { "Ongoing call" })
            .setContentText(if (s.recording.isActive) "Ongoing call · Recording" else "Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setColor(CALL_COLOR)
            .setColorized(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(s.answeredAtMillis.takeIf { it > 0L } ?: System.currentTimeMillis())
            .setContentIntent(activityIntent(s.eventId, answer = false))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
            .addAction(
                if (s.muted) R.drawable.ic_action_unmute else R.drawable.ic_action_mute,
                if (s.muted) "Unmute" else "Mute",
                mute,
            )
            .build()
    }

    /** The caller as Android's call style and Do Not Disturb see them: name, photo, and the number for "contacts only". */
    private fun callerPerson(s: SessionState, portrait: Bitmap?): Person {
        val handle = PhoneNumbers.dialable(s.callerPhone)
        return Person.Builder()
            .setName(s.displayName.ifBlank { "Unknown" })
            .setImportant(true)
            .apply {
                portrait?.let { setIcon(IconCompat.createWithBitmap(it)) }
                if (handle.isNotEmpty()) {
                    setKey(handle)
                    setUri("tel:$handle")
                }
            }
            .build()
    }

    /** What the user chose to show in the corner of the caller's photo: the app's own icon, a green dot, or nothing. */
    private enum class PhotoBadge { APP_ICON, GREEN_DOT, NONE }

    /**
     * In Android's call style the system draws the app's icon in the corner of the caller's photo, from the
     * notification's small icon. A clear small icon leaves that corner empty: that is how the green-dot style (only the
     * dot shows) and "icons off" (nothing shows) work. Some phones add their own app badge whatever the app does.
     */
    private fun smallIcon(badge: PhotoBadge, callStyle: Boolean): Int =
        if (callStyle && badge != PhotoBadge.APP_ICON) R.drawable.ic_notification_clear else R.drawable.ic_notification

    private fun readBadgeStyle(): NotificationBadgeStyle = runCatching {
        val raw = context.getSharedPreferences(SettingsStore.NOTIFICATION_PREF_FILE, Context.MODE_PRIVATE)
            .getString(SettingsStore.NOTIFICATION_PREF_KEY, NotificationBadgeStyle.APP_LOGO.name)
        NotificationBadgeStyle.valueOf(raw ?: NotificationBadgeStyle.APP_LOGO.name)
    }.getOrDefault(NotificationBadgeStyle.APP_LOGO)

    /** False when the user switched the caller-photo icons off in Settings. */
    private fun badgeSwitchedOn(): Boolean = runCatching {
        context.getSharedPreferences(SettingsStore.NOTIFICATION_PREF_FILE, Context.MODE_PRIVATE)
            .getBoolean(SettingsStore.NOTIFICATION_PREF_ENABLED_KEY, true)
    }.getOrDefault(true)

    private fun readPhotoBadge(): PhotoBadge = when {
        !badgeSwitchedOn() -> PhotoBadge.NONE
        readBadgeStyle() == NotificationBadgeStyle.ONLINE_DOT -> PhotoBadge.GREEN_DOT
        else -> PhotoBadge.APP_ICON
    }

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

    /** True when Android lets the call notification ring and pop up even in Do Not Disturb. */
    fun incomingChannelOverridesDnd(): Boolean = runCatching {
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_INCOMING)?.canBypassDnd() == true
    }.getOrDefault(false)

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

    /**
     * Makes a true circular contact portrait. Nothing is painted on it except the optional green "online" dot: in
     * Android's call style the system draws the app's real icon in the corner itself.
     */
    private fun avatar(name: String?, greenDot: Boolean): Bitmap? {
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

            if (greenDot) {
                val cx = size * 0.79f
                val cy = size * 0.79f
                val radius = size * 0.17f
                val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
                dotPaint.color = AndroidColor.WHITE
                canvas.drawCircle(cx, cy, radius + 4f, dotPaint)
                dotPaint.color = AndroidColor.rgb(46, 204, 96)
                canvas.drawCircle(cx, cy, radius, dotPaint)
            }
            output
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "incoming_call_v3"
        const val CHANNEL_BACKUP = "incoming_call_backup_v3"
        private val LEGACY_CHANNELS = listOf("incoming_call", "incoming_call_backup", "incoming_call_v2", "incoming_call_backup_v2")
        private const val TAG = "NotificationHelper"
        const val CHANNEL_ONGOING = "ongoing_call"
        const val CHANNEL_MISSED = "missed_call"
        const val NOTIFICATION_ID = 4101
        private const val MISSED_ID_BASE = 4200
        private val CALL_COLOR = AndroidColor.rgb(0, 82, 88)
        const val EXTRA_EVENT_ID = "event_id"
        const val ACTION_DECLINE = "com.callstudio.app.action.DECLINE"
        const val ACTION_END = "com.callstudio.app.action.END"
        const val ACTION_TOGGLE_MUTE = "com.callstudio.app.action.TOGGLE_MUTE"
    }
}
