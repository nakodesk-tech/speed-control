package com.example

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.example.data.local.AppDatabase

class EduCompanionApp : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDatabase.getDatabase(this)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "edu_companion_overlay_channel",
                "EduCompanion Floating Controls",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active companion floating controls for educational apps"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }
}
