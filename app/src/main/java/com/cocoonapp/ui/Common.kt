@file:OptIn(ExperimentalMaterial3Api::class)

package com.cocoonapp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
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

/** Types an exact hh:mm, a time of day or a duration. The result is brought into [range]. */
@Composable
fun TimeDialog(
    minutes: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    range: IntRange = 0..MAX_TIME_INPUT,
) {
    val initial = minutes.coerceIn(0, MAX_TIME_INPUT)
    val state = rememberTimePickerState(initialHour = initial / 60, initialMinute = initial % 60, is24Hour = true)
    val titleContent: (@Composable () -> Unit)? = if (title != null) {
        { Text(title) }
    } else {
        null
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick((state.hour * 60 + state.minute).coerceIn(range)) }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        title = titleContent,
        text = { TimeInput(state) },
    )
}

/** Largest value [TimeDialog] can express: 23:59. */
const val MAX_TIME_INPUT = 23 * 60 + 59
