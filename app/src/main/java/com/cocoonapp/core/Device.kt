package com.cocoonapp.core

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process

object Device {
    /** Xiaomi HyperOS, which needs extra setup before it lets the gate show up reliably. */
    val isHyperOs: Boolean by lazy { systemProperty("ro.mi.os.version.name").isNotEmpty() }

    /**
     * HyperOS "Autostart". Swiping the app out of recents kills its process, accessibility
     * service included; without autostart the system is not allowed to bring the service back
     * and marks it as not working. Null when it cannot be read (not HyperOS, or the op is gone).
     */
    fun autostartAllowed(context: Context): Boolean? {
        if (!isHyperOs) return null
        return runCatching {
            val appOps = context.getSystemService(AppOpsManager::class.java)
            val mode = AppOpsManager::class.java
                .getMethod("checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java)
                .invoke(appOps, OP_AUTO_START, Process.myUid(), context.packageName) as Int
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrNull()
    }

    /** Opens the HyperOS autostart list; false if this build does not have it. */
    fun openAutostart(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent().setComponent(ComponentName(SECURITY_CENTER, AUTOSTART_ACTIVITY))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull().orEmpty()

    /** MIUI's own app op behind the autostart switch. */
    private const val OP_AUTO_START = 10008
    private const val SECURITY_CENTER = "com.miui.securitycenter"
    private const val AUTOSTART_ACTIVITY = "com.miui.permcenter.autostart.AutoStartManagementActivity"
}
