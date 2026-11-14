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

    fun init(context: Context) {
        val channel = NotificationChannel(
            CHANNEL,
            context.getString(R.string.channel_warn),
            NotificationManager.IMPORTANCE_HIGH,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun warn(context: Context, pkg: String, leftMs: Long) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val minutes = ((leftMs + Logic.MINUTE - 1) / Logic.MINUTE).toInt().coerceAtLeast(1)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(Apps.label(context, pkg))
            .setContentText(context.getString(R.string.notif_left, minutes))
            .setAutoCancel(true)
            .setTimeoutAfter(leftMs)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(pkg.hashCode(), notification)
    }

    fun cancel(context: Context, pkg: String) {
        context.getSystemService(NotificationManager::class.java).cancel(pkg.hashCode())
    }
}
