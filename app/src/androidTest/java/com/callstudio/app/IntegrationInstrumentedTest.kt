package com.callstudio.app

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.callstudio.app.notifications.NotificationHelper
import com.callstudio.app.notifications.RequestCodes
import com.callstudio.app.scheduling.AlarmReceiver
import com.callstudio.app.scheduling.AndroidAlarmBackend
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IntegrationInstrumentedTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun callScreenIntentsDifferPerEventAndPurpose() {
        val a = CallActivity.intent(context, 1)
        val b = CallActivity.intent(context, 2)
        val answer = CallActivity.intent(context, 1, answer = true)
        assertFalse(a.filterEquals(b))
        assertFalse(a.filterEquals(answer))
    }

    @Test fun alarmIsRegisteredThenRemovedOnCancel() {
        val backend = AndroidAlarmBackend(context)
        val id = 987_654L
        backend.set(id, System.currentTimeMillis() + 3_600_000L, exact = false)
        assertNotNull(lookup(id))
        backend.cancel(id)
        assertNull(lookup(id))
    }

    private fun lookup(id: Long): PendingIntent? {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .setData(Uri.parse("callstudio://alarm/$id"))
            .putExtra(NotificationHelper.EXTRA_EVENT_ID, id)
        return PendingIntent.getBroadcast(
            context, RequestCodes.forEvent(id, RequestCodes.ALARM), intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
