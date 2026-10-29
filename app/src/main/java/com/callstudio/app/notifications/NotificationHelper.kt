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
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.callstudio.app.CallActivity
import com.callstudio.app.R
import com.callstudio.app.data.PhotoStore
import com.callstudio.app.permissions.SystemAccess
import com.callstudio.app.session.SessionState

class NotificationHelper(private val context: Context, private val photos: PhotoStore) {
    private val manager = NotificationManagerCompat.from(context)

    fun ensureChannels() {
        val system = context.getSystemService(NotificationManager::class.java)
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
        system.createNotificationChannels(listOf(incoming, backup, ongoing))
    }

    fun buildIncoming(s: SessionState, backup: Boolean): Notification {
        val open = activityIntent(s.eventId, answer = false)
        val answer = activityIntent(s.eventId, answer = true)
        val decline = broadcastIntent(s.eventId, ACTION_DECLINE, RequestCodes.DECLINE)
        val b = NotificationCompat.Builder(context, if (backup) CHANNEL_BACKUP else CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.displayName)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(open)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
        // Without full-screen access this stays a heads-up notification.
        if (SystemAccess.fullScreenAllowed(context)) b.setFullScreenIntent(open, true)
        if (backup) {
            b.addAction(0, "Decline", decline).addAction(0, "Answer", answer)
        } else {
            b.setStyle(NotificationCompat.CallStyle.forIncomingCall(person(s), decline, answer))
        }
        val n = b.build()
        if (backup) n.flags = n.flags or Notification.FLAG_INSISTENT
        return n
    }

    fun buildOngoing(s: SessionState): Notification {
        val hangUp = broadcastIntent(s.eventId, ACTION_END, RequestCodes.HANG_UP)
        val mute = broadcastIntent(s.eventId, ACTION_TOGGLE_MUTE, RequestCodes.MUTE)
        val recording = s.recording.isActive
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(s.displayName)
            .setContentText(if (recording) "Ongoing call · Recording" else "Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setShowWhen(true)
            .setWhen(s.answeredAtMillis)
            .setContentIntent(activityIntent(s.eventId, answer = false))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person(s), hangUp))
            .addAction(0, if (s.muted) "Unmute" else "Mute", mute)
            .build()
    }

    /** Placeholder used only to satisfy the foreground-service contract when the call is already over. */
    fun buildEnded(): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Call ended")
            .setOngoing(false)
            .build()

    @SuppressLint("MissingPermission")
    fun postBackupIncoming(s: SessionState) {
        if (!manager.areNotificationsEnabled()) return
        manager.notify(NOTIFICATION_ID, buildIncoming(s, backup = true))
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

    private fun person(s: SessionState): Person {
        val builder = Person.Builder().setName(s.displayName.ifBlank { "Unknown" }).setImportant(true)
        avatar(s.callerPhotoFile)?.let { builder.setIcon(IconCompat.createWithBitmap(it)) }
        return builder.build()
    }

    private fun avatar(name: String?): Bitmap? {
        val file = photos.file(name) ?: return null
        return try {
            val bmp = BitmapFactory.decodeFile(file.path) ?: return null
            Bitmap.createScaledBitmap(bmp, 192, 192, true)
        } catch (e: Exception) {
            null
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "incoming_call"
        const val CHANNEL_BACKUP = "incoming_call_backup"
        const val CHANNEL_ONGOING = "ongoing_call"
        const val NOTIFICATION_ID = 4101
        const val EXTRA_EVENT_ID = "event_id"
        const val ACTION_DECLINE = "com.callstudio.app.action.DECLINE"
        const val ACTION_END = "com.callstudio.app.action.END"
        const val ACTION_TOGGLE_MUTE = "com.callstudio.app.action.TOGGLE_MUTE"
    }
}
