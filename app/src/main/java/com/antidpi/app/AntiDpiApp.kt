package com.antidpi.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.antidpi.app.core.AppLogger
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform

class AntiDpiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLogger.init(this)
        installCrashHandler()

        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(
                NotificationChannel(
                    TG_CHANNEL_ID,
                    getString(R.string.tg_channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    setShowBadge(false)
                }
            )
        }
        AppLogger.i("App", "Application started")
    }

    private fun installCrashHandler() {
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            AppLogger.recordCrash(thread, throwable)
            prev?.uncaughtException(thread, throwable)
        }
    }

    companion object {
        const val TG_CHANNEL_ID = "tg_proxy"
    }
}
