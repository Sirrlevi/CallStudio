package com.callstudio.app.session

import android.content.Context
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.callstudio.app.CallActivity
import com.callstudio.app.diag.CallDiagnostics

/**
 * Opens the call screen over whatever is on screen when a call starts ringing, like a real incoming call.
 *
 * Android only lets an app open a screen from the background when the app has a visible window (or is in the
 * foreground). With "Display over other apps" allowed, a 1-pixel invisible window is shown for a moment, which counts
 * as a visible window. Without that access the call still appears as a heads-up notification, and when the phone is
 * locked or off as a full-screen call screen through the notification.
 */
object CallScreenLauncher {
    private val main = Handler(Looper.getMainLooper())

    /** Never throws and never blocks; what happened is written to the call report. */
    fun show(context: Context, eventId: Long) {
        val app = context.applicationContext
        main.post {
            try {
                if (Settings.canDrawOverlays(app)) showWithOverlay(app, eventId) else start(app, eventId, "app is not allowed to draw over other apps")
            } catch (t: Throwable) {
                CallDiagnostics.log("Call screen could not be opened", "${t.javaClass.simpleName}: ${t.message}")
            }
        }
    }

    private fun showWithOverlay(app: Context, eventId: Long) {
        val windows = app.getSystemService(WindowManager::class.java)
        val dot = View(app)
        val params = WindowManager.LayoutParams(
            1,
            1,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        val added = try {
            windows.addView(dot, params)
            true
        } catch (t: Throwable) {
            CallDiagnostics.log("Overlay window was refused", "${t.javaClass.simpleName}: ${t.message}")
            false
        }
        // Give Android a moment to show the window before asking it to open the screen.
        main.postDelayed({ start(app, eventId, if (added) "with a visible window" else "overlay window refused") }, if (added) 150L else 0L)
        if (added) main.postDelayed({ runCatching { windows.removeView(dot) } }, 4000L)
    }

    private fun start(app: Context, eventId: Long, how: String) {
        try {
            app.startActivity(CallActivity.intent(app, eventId))
            CallDiagnostics.log("Opening the call screen", how)
        } catch (t: Throwable) {
            CallDiagnostics.log("Call screen could not be opened", "$how: ${t.javaClass.simpleName}: ${t.message}")
        }
    }
}
