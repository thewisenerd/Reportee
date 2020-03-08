package com.example.reportee

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.reportee.Constants.LOGTAG

class ForegroundCallService: Service() {
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(LOGTAG, "onStartCommand")
        if (intent != null) {
            super.onStartCommand(intent, flags, startId)
        }

        val notif = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID_DEFAULT)
            .setContentTitle("Reportee")
            .setContentText("yay android oreo+ permissions")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(null)
            .build()

        startForeground(1, notif)

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}