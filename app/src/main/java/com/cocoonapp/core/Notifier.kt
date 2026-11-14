package com.cocoonapp.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.cocoonapp.R
import com.cocoonapp.ui.Apps

object Notifier {
    private const val CHANNEL = "session_warning"
    private const val STATUS_CHANNEL = "session_status"
    private const val STATUS_TAG = "status"

    /** The warning only has to catch the eye: the status notification keeps showing the time. */
    private const val WARN_TIMEOUT_MS = 10_000L

    fun init(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.channel_warn), NotificationManager.IMPORTANCE_HIGH),
        )
        manager.createNotificationChannel(
            NotificationChannel(STATUS_CHANNEL, context.getString(R.string.channel_status), NotificationManager.IMPORTANCE_LOW),
        )
    }

    /** Whole minutes shown for [leftMs], rounded up. */
    fun minutes(leftMs: Long): Int = ((leftMs + Logic.MINUTE - 1) / Logic.MINUTE).toInt().coerceAtLeast(1)

    private fun allowed(context: Context): Boolean = Build.VERSION.SDK_INT < 33 ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun warn(context: Context, pkg: String, leftMs: Long) {
        if (!allowed(context)) return
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Apps.label(context, pkg))
            .setContentText(context.getString(R.string.notif_left, minutes(leftMs)))
            .setAutoCancel(true)
            .setTimeoutAfter(leftMs.coerceAtMost(WARN_TIMEOUT_MS))
            .build()
        context.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), notification)
    }

    fun cancel(context: Context, pkg: String) {
        context.getSystemService(NotificationManager::class.java).cancel(pkg.hashCode())
    }

    /** Silent, ongoing "time left" for the session of the app in the foreground. */
    fun status(context: Context, pkg: String, leftMs: Long) {
        if (!allowed(context)) return
        val notification = Notification.Builder(context, STATUS_CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Apps.label(context, pkg))
            .setContentText(context.getString(R.string.notif_left, minutes(leftMs)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            // Where the system shows it, the countdown runs by itself between updates.
            .setShowWhen(true)
            .setWhen(System.currentTimeMillis() + leftMs)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(STATUS_TAG, 0, notification)
    }

    fun clearStatus(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(STATUS_TAG, 0)
    }
}
