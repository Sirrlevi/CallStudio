package com.callstudio.app

import android.app.Application
import com.callstudio.app.scheduling.CallNotifications

class CallStudioApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CallNotifications.createChannels(this)
    }
}
