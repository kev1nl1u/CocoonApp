@file:OptIn(ExperimentalMaterial3Api::class)

package com.cocoonapp.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocoonapp.R
import com.cocoonapp.core.Logic
import com.cocoonapp.data.AppState
import com.cocoonapp.data.Mode
import com.cocoonapp.data.Rule
import com.cocoonapp.data.Store
import java.time.ZoneId

private val MAX_VALUES = listOf(5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240, 300, 360, 480)
private val COOLDOWN_VALUES = listOf(0, 1, 2, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)
private val WARN_VALUES = listOf(1, 2, 3, 5, 10, 15)

@Composable
fun EditorScreen(initial: Rule, isNew: Boolean, onDone: () -> Unit) {
    val context = LocalContext.current
    var rule by remember { mutableStateOf(initial) }
    val label = remember(initial.pkg) { Apps.label(context, initial.pkg) }

    val states by Store.states.collectAsStateWithLifecycle()
    val mode by Store.mode.collectAsStateWithLifecycle()
    val now by rememberNow()
    // Max Focus: a blocked app keeps its rule until the block is over.
    val locked = !isNew && mode == Mode.MAX &&
        Logic.status(initial, states[initial.pkg] ?: AppState(), now, ZoneId.systemDefault()).isBlocked
    val editable = !locked

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AppIcon(rule.pkg, 32.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    if (!isNew) {
                        IconButton(
                            onClick = {
                                Store.deleteRule(rule.pkg)
                                onDone()
                            },
                            enabled = editable,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                        }
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = {
                    Store.saveRule(rule)
                    onDone()
                },
                enabled = editable,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(56.dp),
            ) {
                Text(stringResource(if (isNew) R.string.add else R.string.save))
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (locked) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(R.string.rule_locked),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }

            val rangeOnly = rule.scheduleEnabled && !rule.countAllDay
            Section(
                title = stringResource(R.string.max_time),
                value = stringResource(
                    if (rangeOnly) R.string.per_range else R.string.per_day,
                    minutesText(rule.maxMinutes),
                ),
            ) {
                StepSlider(MAX_VALUES, rule.maxMinutes, { rule = rule.copy(maxMinutes = it) }, enabled = editable)
            }

            Section(
                title = stringResource(R.string.cooldown),
                value = if (rule.cooldownMinutes == 0) stringResource(R.string.none) else minutesText(rule.cooldownMinutes),
            ) {
                StepSlider(COOLDOWN_VALUES, rule.cooldownMinutes, { rule = rule.copy(cooldownMinutes = it) }, enabled = editable)
            }

            Section(
                title = stringResource(R.string.schedule),
                value = if (rule.scheduleEnabled) null else stringResource(R.string.schedule_always),
                checked = rule.scheduleEnabled,
                onCheckedChange = { rule = rule.copy(scheduleEnabled = it) },
                enabled = editable,
            ) {
                if (rule.scheduleEnabled) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TimeButton(rule.startMinute, editable) { rule = rule.copy(startMinute = it) }
                        Text("–", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TimeButton(rule.endMinute, editable) { rule = rule.copy(endMinute = it) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.count_label),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(false, true).forEachIndexed { index, allDay ->
                            SegmentedButton(
                                selected = rule.countAllDay == allDay,
                                onClick = { rule = rule.copy(countAllDay = allDay) },
                                shape = SegmentedButtonDefaults.itemShape(index, 2),
                                enabled = editable,
                            ) {
                                Text(stringResource(if (allDay) R.string.count_day else R.string.count_range))
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Section(
                title = stringResource(R.string.warn),
                value = if (rule.warnEnabled) minutesText(rule.warnMinutes) else null,
                checked = rule.warnEnabled,
                onCheckedChange = { rule = rule.copy(warnEnabled = it) },
                enabled = editable,
            ) {
                if (rule.warnEnabled) {
                    StepSlider(WARN_VALUES, rule.warnMinutes, { rule = rule.copy(warnMinutes = it) }, enabled = editable)
                }
            }
        }
    }
}

/** Card with a title, an optional current value and an optional on/off switch. */
@Composable
private fun Section(
    title: String,
    value: String?,
    checked: Boolean? = null,
    onCheckedChange: (Boolean) -> Unit = {},
    enabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (value != null) {
                    Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (checked != null) {
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
                }
            }
            content()
        }
    }
}

@Composable
private fun TimeButton(minute: Int, enabled: Boolean, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FilledTonalButton(onClick = { open = true }, enabled = enabled) {
        Text(clock(minute), style = MaterialTheme.typography.titleMedium)
    }
    if (open) {
        val state = rememberTimePickerState(initialHour = minute / 60, initialMinute = minute % 60, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        onPick(state.hour * 60 + state.minute)
                        open = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { open = false }) { Text(stringResource(R.string.cancel)) }
            },
            text = { TimeInput(state) },
        )
    }
}
