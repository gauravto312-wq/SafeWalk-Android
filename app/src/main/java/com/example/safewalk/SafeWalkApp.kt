package com.example.safewalk

import android.app.Application
import com.example.safewalk.core.common.ServiceLocator
import com.example.safewalk.core.notification.EmergencyNotificationHelper
import com.example.safewalk.core.notification.FcmTokenManager

class SafeWalkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        EmergencyNotificationHelper.createNotificationChannels(this)
        FcmTokenManager.fetchAndSyncToken()
    }
}
