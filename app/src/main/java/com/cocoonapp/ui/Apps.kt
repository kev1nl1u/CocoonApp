package com.cocoonapp.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

data class AppEntry(val pkg: String, val label: String)

/** Labels and icons of installed apps. */
object Apps {
    private const val ICON_PX = 144
    private val icons = LruCache<String, ImageBitmap>(200)

    fun label(context: Context, pkg: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    fun cachedIcon(pkg: String): ImageBitmap? = icons.get(pkg)

    fun icon(context: Context, pkg: String): ImageBitmap? = cachedIcon(pkg) ?: try {
        context.packageManager.getApplicationIcon(pkg).toBitmap(ICON_PX, ICON_PX).asImageBitmap()
            .also { icons.put(pkg, it) }
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }

    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(launcher, 0)
            .map { AppEntry(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
    }
}
