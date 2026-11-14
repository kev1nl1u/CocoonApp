@file:OptIn(ExperimentalMaterial3Api::class)

package com.cocoonapp.ui

import android.Manifest
import android.app.Activity
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocoonapp.R
import com.cocoonapp.core.Gate
import com.cocoonapp.core.GuardService
import com.cocoonapp.core.Logic
import com.cocoonapp.data.AppState
import com.cocoonapp.data.Mode
import com.cocoonapp.data.Rule
import com.cocoonapp.data.Store
import java.time.ZoneId

@Composable
fun HomeScreen(onAdd: () -> Unit, onEdit: (Rule) -> Unit) {
    val context = LocalContext.current
    val rules by Store.rules.collectAsStateWithLifecycle()
    val states by Store.states.collectAsStateWithLifecycle()
    val mode by Store.mode.collectAsStateWithLifecycle()
    val now by rememberNow()
    val zone = ZoneId.systemDefault()
    val gates = rules.associate { it.pkg to Logic.status(it, states[it.pkg] ?: AppState(), now, zone) }

    var detectionOn by remember { mutableStateOf(isGuardEnabled(context)) }
    var notificationsOn by remember { mutableStateOf(notificationsEnabled(context)) }
    LifecycleResumeEffect(Unit) {
        detectionOn = isGuardEnabled(context)
        notificationsOn = notificationsEnabled(context)
        onPauseOrDispose { }
    }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsOn = granted
        // The system stops showing the dialog after repeated denials; settings is the only way left.
        val canAskAgain = (context as? Activity)
            ?.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) ?: false
        if (!granted && !canAskAgain) openNotificationSettings(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium) })
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_app)) },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 4.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!detectionOn) item(key = "detection") {
                PermissionCard(
                    title = stringResource(R.string.perm_a11y_title),
                    body = stringResource(R.string.perm_a11y_body),
                    // Sideloaded apps hit "restricted settings" from Android 13 on.
                    hint = if (Build.VERSION.SDK_INT >= 33) stringResource(R.string.perm_a11y_restricted) else null,
                    onHintClick = { openAppInfo(context) },
                    onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                )
            }
            if (!notificationsOn) item(key = "notifications") {
                PermissionCard(
                    title = stringResource(R.string.perm_notif_title),
                    body = stringResource(R.string.perm_notif_body),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= 33) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else openNotificationSettings(context)
                    },
                )
            }
            item(key = "mode") {
                ModeSwitch(mode, locked = mode == Mode.MAX && gates.values.any { it.isBlocked })
            }
            if (rules.isEmpty()) item(key = "empty") { Empty() }
            items(rules, key = { it.pkg }) { rule ->
                val gate = gates[rule.pkg] ?: Gate.Free
                RuleCard(
                    rule = rule,
                    usedMs = Logic.usedMs(rule, states[rule.pkg] ?: AppState(), now, zone),
                    gate = gate,
                    now = now,
                    locked = mode == Mode.MAX && gate.isBlocked,
                    onClick = { onEdit(rule) },
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    body: String,
    hint: String? = null,
    onHintClick: () -> Unit = {},
    onClick: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(body, style = MaterialTheme.typography.bodySmall)
                if (hint != null) {
                    Text(
                        hint,
                        style = MaterialTheme.typography.bodySmall,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier
                            .clickable(onClick = onHintClick)
                            .padding(vertical = 6.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Button(onClick = onClick) { Text(stringResource(R.string.enable)) }
        }
    }
}

@Composable
private fun ModeSwitch(mode: Mode, locked: Boolean) {
    Column {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Mode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = { Store.setMode(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, Mode.entries.size),
                    enabled = !locked,
                ) {
                    Text(stringResource(if (option == Mode.MAX) R.string.mode_max else R.string.mode_standard))
                }
            }
        }
        Text(
            text = stringResource(
                when {
                    locked -> R.string.mode_locked
                    mode == Mode.MAX -> R.string.mode_max_hint
                    else -> R.string.mode_standard_hint
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp),
        )
    }
}

@Composable
private fun Empty() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RuleCard(rule: Rule, usedMs: Long, gate: Gate, now: Long, locked: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val label = remember(rule.pkg) { Apps.label(context, rule.pkg) }
    val usage = stringResource(
        R.string.usage,
        minutesText((usedMs / Logic.MINUTE).toInt()),
        minutesText(rule.maxMinutes),
    )
    val status = when {
        !rule.enabled -> stringResource(R.string.status_off)
        gate is Gate.Cooldown -> stringResource(R.string.status_cooldown, countdown(gate.until - now))
        gate is Gate.Limit -> stringResource(R.string.status_limit)
        gate is Gate.Running -> stringResource(R.string.status_session, minutesText(ceilMinutes(gate.leftMs)))
        gate is Gate.Free -> stringResource(R.string.status_out, clock(rule.startMinute), clock(rule.endMinute))
        else -> null
    }
    val accent = if (rule.enabled && gate.isBlocked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

    Card(onClick = onClick, shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(rule.pkg, 44.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = listOfNotNull(usage, status).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (rule.enabled && gate.isBlocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    val fraction = (usedMs.toFloat() / (rule.maxMinutes * Logic.MINUTE)).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(accent),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Switch(
                checked = rule.enabled,
                onCheckedChange = { Store.saveRule(rule.copy(enabled = it)) },
                enabled = !locked,
            )
        }
    }
}

private fun isGuardEnabled(context: Context): Boolean {
    val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        ?: return false
    val guard = ComponentName(context, GuardService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == guard }
}

private fun notificationsEnabled(context: Context): Boolean =
    context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

/** The system's "allow restricted settings" switch for sideloaded apps lives on this page. */
private fun openAppInfo(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )
}

private fun openNotificationSettings(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
    )
}
