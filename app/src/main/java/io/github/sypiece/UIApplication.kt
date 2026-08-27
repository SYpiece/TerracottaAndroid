package io.github.sypiece

import android.app.Application
import android.util.Log

class UIApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { _, throwable ->
            Log.e("TerracottaAndroid", "uncaught exception", throwable)
        }

        Terracotta.initialize(this)
    }
}