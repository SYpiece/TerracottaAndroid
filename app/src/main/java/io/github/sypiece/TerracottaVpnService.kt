package io.github.sypiece

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import androidx.core.app.NotificationCompat

@SuppressLint("VpnServicePolicy")
class TerracottaVpnService : VpnService() {
    var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, buildNotification(Terracotta.getState()))
        Terracotta.addStateListener { newState ->
            notificationManager?.notify(NOTIFICATION_ID, buildNotification(newState))
        }
        Terracotta.vpnRequestListener = Terracotta.VpnRequestListener {
            Builder().setSession("Terracotta Connection")
        }
    }

    private fun buildNotification(state: TerracottaState): Notification {
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDefaults(0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
        return when (state) {
//            is TerracottaState.Host.OK -> builder
//                .setContentTitle("房间：${state.room}")
//                .setContentText("人数：${state.profiles.size}")
//            is TerracottaState.Guest.OK -> builder
//                .setContentTitle("")
//                .setContentText("人数：${state.profiles.size}")
            else -> builder
                .setContentTitle("未知状态")
                .setContentText(state.toString())
        }.build()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        return START_STICKY
    }
}