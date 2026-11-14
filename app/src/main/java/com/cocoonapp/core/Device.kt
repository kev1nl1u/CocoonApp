package com.cocoonapp.core

import android.annotation.SuppressLint

object Device {
    /** Xiaomi HyperOS, which needs extra setup before it lets the gate show up reliably. */
    val isHyperOs: Boolean by lazy { systemProperty("ro.mi.os.version.name").isNotEmpty() }

    @SuppressLint("PrivateApi")
    private fun systemProperty(name: String): String = runCatching {
        Class.forName("android.os.SystemProperties")
            .getMethod("get", String::class.java)
            .invoke(null, name) as? String
    }.getOrNull().orEmpty()
}
