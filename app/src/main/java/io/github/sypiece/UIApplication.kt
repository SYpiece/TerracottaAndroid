package io.github.sypiece

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import net.burningtnt.terracotta.TerracottaAndroidAPI

class UIApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("TerracottaService", "uncaught exception", throwable)
        }

        Terracotta.metadata = TerracottaAndroidAPI.initialize(this) {
            val request = TerracottaAndroidAPI.getPendingVpnServiceRequest()
            try {
                request.reject()
            } catch (e: Throwable) {
                Log.e("TerracottaService", "reject failed", e)
            }
        }

        val intent = Intent(this, TerracottaService::class.java)
        startForegroundService(intent)
        bindService(intent, object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?
            ) {}

            override fun onServiceDisconnected(name: ComponentName?) {}
        }, BIND_AUTO_CREATE)
    }
}