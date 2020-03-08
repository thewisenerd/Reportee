package com.example.reportee

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID_DEFAULT,
            "Reportee Notification Channel",
            NotificationManager.IMPORTANCE_DEFAULT
        )

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(serviceChannel)
    }
}