package io.github.sypiece

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.PowerManager
import androidx.core.app.NotificationCompat

@SuppressLint("VpnServicePolicy")
class TerracottaService : VpnService() {
    var notificationManager: NotificationManager? = null
    var wakeLock: PowerManager.WakeLock? = null

    private val stateListener = Terracotta.StateListener { newState ->
        notificationManager?.notify(NOTIFICATION_ID, buildNotification(newState))
        when(newState) {
            is TerracottaState.Host.OK, is TerracottaState.Guest.OK -> {
                wakeLock?.let {
                    if (!it.isHeld) @SuppressLint("WakelockTimeout") {
                        it.acquire()
                    }
                }
            }
            else -> {
                wakeLock?.let {
                    if (it.isHeld) {
                        it.release()
                    }
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TerracotaAndroid::wakeLock")

        notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        startForeground(NOTIFICATION_ID, buildNotification(Terracotta.getState()))
        Terracotta.addStateListener(stateListener)

        Terracotta.vpnRequestListener = Terracotta.VpnRequestListener {
            Builder().setSession("Terracotta Connection")
        }
    }

    private fun buildNotification(state: TerracottaState): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, UIActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = NotificationCompat.Builder(this, notificationChannelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setDefaults(0)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(false)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
        return when (state) {
            is TerracottaState.Host.OK -> builder
                .setContentTitle("房间创建成功")
                .setStyle(NotificationCompat.InboxStyle()
                    .setBigContentTitle("房间：${state.room}")
                    .also { inboxStyle ->
                        state.profiles.forEach {
                            inboxStyle.addLine(it.name)
                        }
                    }
                    .setSummaryText("人数：${state.profiles.size}")
                )
            is TerracottaState.Guest.OK -> builder
                .setContentTitle("加入房间成功")
                .setStyle(NotificationCompat.InboxStyle()
                    .also { inboxStyle ->
                        state.profiles.forEach {
                            inboxStyle.addLine(it.name)
                        }
                    }
                    .setSummaryText("人数：${state.profiles.size}")
                )
            else -> builder
                .setContentTitle("闲置中...")
        }.build()
    }


    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        Terracotta.removeStateListener(stateListener)
        notificationManager?.cancel(NOTIFICATION_ID)
    }
}