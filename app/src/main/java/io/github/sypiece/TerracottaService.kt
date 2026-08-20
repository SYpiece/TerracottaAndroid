package io.github.sypiece

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import net.burningtnt.terracotta.TerracottaAndroidAPI
import org.json.JSONObject
import java.io.Reader

class TerracottaService : Service() {
    companion object {
        const val CHANNEL_ID = "TerracottaService"
        const val NOTIFICATION_ID = 2325
    }

    var terracotta: Terracotta? = null

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "channelName",
            NotificationManager.IMPORTANCE_LOW
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Terracotta 运行中")
            .setContentText("Terracotta 正在后台运行")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        return super.onStartCommand(intent, flags, startId)
    }

    inner class LocalBinder : Binder() {
    }

    override fun onBind(intent: Intent?): IBinder {
        return LocalBinder()
    }
}