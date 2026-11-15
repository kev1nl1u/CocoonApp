package com.cocoonapp

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.cocoonapp.core.Notifier
import com.cocoonapp.data.Store

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        Notifier.init(this)
        recordCrashes()
    }

    /**
     * A crash takes the accessibility service down with it, and from the outside that only
     * looks like limits that stopped working. The trace is kept to be shown at the next launch.
     */
    private fun recordCrashes() {
        val build = runCatching { PackageInfoCompat.getLongVersionCode(packageManager.getPackageInfo(packageName, 0)) }
            .getOrDefault(0L)
        val header = "Cocoon $build · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { Store.saveCrash("$header\n${Log.getStackTraceString(error)}".take(MAX_REPORT_CHARS)) }
            previous?.uncaughtException(thread, error)
        }
    }

    private companion object {
        const val MAX_REPORT_CHARS = 6000
    }
}
