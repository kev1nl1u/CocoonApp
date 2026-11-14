package com.cocoonapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import com.cocoonapp.R
import com.cocoonapp.core.Logic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

/** Wall clock that advances once per second. */
@Composable
fun rememberNow(): State<Long> = produceState(System.currentTimeMillis()) {
    while (true) {
        delay(1000 - System.currentTimeMillis() % 1000)
        value = System.currentTimeMillis()
    }
}

@Composable
fun minutesText(minutes: Int): String = when {
    minutes < 60 -> stringResource(R.string.fmt_min, minutes)
    minutes % 60 == 0 -> stringResource(R.string.fmt_h, minutes / 60)
    else -> stringResource(R.string.fmt_h_min, minutes / 60, minutes % 60)
}

fun ceilMinutes(ms: Long): Int = ((ms.coerceAtLeast(0) + Logic.MINUTE - 1) / Logic.MINUTE).toInt()

fun clock(minuteOfDay: Int): String = String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60)

fun clock(epochMs: Long, zone: ZoneId): String {
    val time = Instant.ofEpochMilli(epochMs).atZone(zone).toLocalTime()
    return clock(time.hour * 60 + time.minute)
}

fun countdown(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    val h = seconds / 3600
    val m = seconds % 3600 / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.ROOT, "%d:%02d", m, s)
}

@Composable
fun AppIcon(pkg: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap by produceState(Apps.cachedIcon(pkg), pkg) {
        if (value == null) value = withContext(Dispatchers.IO) { Apps.icon(context, pkg) }
    }
    val icon = bitmap
    if (icon != null) {
        Image(bitmap = icon, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(percent = 28))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        )
    }
}

/** Slider that snaps to the entries of [values]. */
@Composable
fun StepSlider(
    values: List<Int>,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val index = values.indexOfLast { it <= value }.coerceAtLeast(0)
    Slider(
        value = index.toFloat(),
        onValueChange = { onValueChange(values[it.roundToInt().coerceIn(values.indices)]) },
        modifier = modifier,
        enabled = enabled,
        valueRange = 0f..values.lastIndex.toFloat(),
        steps = (values.size - 2).coerceAtLeast(0),
    )
}
