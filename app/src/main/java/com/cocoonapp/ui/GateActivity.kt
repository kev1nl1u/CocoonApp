package com.cocoonapp.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cocoonapp.R
import com.cocoonapp.core.Gate
import com.cocoonapp.core.GuardService
import com.cocoonapp.core.Logic
import com.cocoonapp.data.AppState
import com.cocoonapp.data.Mode
import com.cocoonapp.data.Rule
import com.cocoonapp.data.Store
import kotlinx.coroutines.delay
import java.time.ZoneId

/** Full-screen stop in front of a limited app: pick a session, or wait out a block. */
class GateActivity : ComponentActivity() {
    private var pkg by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
        setContent {
            CocoonTheme {
                // Finishing reveals the limited app, which sits right below this task.
                GateScreen(pkg, onOpen = ::finish, onExit = ::leave)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pkg = intent.getStringExtra(EXTRA_PKG).orEmpty()
    }

    override fun onStop() {
        super.onStop()
        // Never linger in the background: the gate is rebuilt whenever the app comes back.
        if (!isChangingConfigurations) finish()
        GuardService.resyncSoon()
    }

    private fun leave() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        finish()
    }

    companion object {
        private const val EXTRA_PKG = "pkg"

        fun intent(context: Context, pkg: String): Intent = Intent(context, GateActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(EXTRA_PKG, pkg)
    }
}

@Composable
private fun GateScreen(pkg: String, onOpen: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current
    val rules by Store.rules.collectAsStateWithLifecycle()
    val states by Store.states.collectAsStateWithLifecycle()
    val mode by Store.mode.collectAsStateWithLifecycle()
    val now by rememberNow()
    val zone = ZoneId.systemDefault()

    val rule = rules.firstOrNull { it.pkg == pkg }
    val state = states[pkg] ?: AppState()
    val gate = if (rule == null) Gate.Free else Logic.status(rule, state, now, zone)

    // Nothing to hold back (session granted, block over, range ended): step aside.
    val open = gate is Gate.Free || gate is Gate.Running
    LaunchedEffect(open) { if (open) onOpen() }
    BackHandler(onBack = onExit)

    fun start(minutes: Int, bypass: Boolean) {
        Store.update(pkg) { r, s -> Logic.startSession(r, s, minutes, bypass, System.currentTimeMillis(), zone) }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 28.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            AppIcon(pkg, 64.dp)
            Spacer(Modifier.height(12.dp))
            Text(
                remember(pkg) { Apps.label(context, pkg) },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(36.dp))

            when (gate) {
                is Gate.Pick -> if (rule != null) {
                    PickContent(rule, gate.budgetLeftMs, onStart = { start(it, bypass = false) }, onExit = onExit)
                }
                is Gate.Cooldown -> BlockedContent(
                    title = stringResource(R.string.gate_cooldown),
                    countdown = countdown(gate.until - now),
                    detail = null,
                    mode = mode,
                    state = state,
                    onBypass = { start(Logic.BYPASS_MINUTES, bypass = true) },
                    onExit = onExit,
                )
                is Gate.Limit -> BlockedContent(
                    title = stringResource(R.string.gate_limit),
                    countdown = null,
                    detail = stringResource(R.string.gate_until, clock(gate.until, zone)),
                    mode = mode,
                    state = state,
                    onBypass = { start(Logic.BYPASS_MINUTES, bypass = true) },
                    onExit = onExit,
                )
                else -> Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ColumnScope.PickContent(rule: Rule, budgetLeftMs: Long, onStart: (Int) -> Unit, onExit: () -> Unit) {
    val fixed = Logic.fixedSession(rule, budgetLeftMs)
    val choices = remember(budgetLeftMs, fixed) { fixed?.let { listOf(it) } ?: Logic.sessionChoices(budgetLeftMs) }
    var picked by remember { mutableIntStateOf(choices.lastOrNull { it <= DEFAULT_SESSION_MINUTES } ?: choices.first()) }
    val minutes = picked.coerceAtMost(choices.last())
    var typing by remember { mutableStateOf(false) }

    Text(
        stringResource(if (fixed == null) R.string.gate_question else R.string.session),
        style = MaterialTheme.typography.headlineSmall,
    )
    Spacer(Modifier.height(12.dp))
    // The slider offers round lengths; tapping the value types an exact one.
    Surface(
        onClick = { typing = true },
        enabled = fixed == null && choices.last() > 1,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            minutesText(minutes),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
        )
    }
    if (choices.size > 1) {
        StepSlider(choices, minutes, { picked = it }, Modifier.padding(top = 20.dp))
    }
    if (typing) {
        TimeDialog(
            minutes = minutes,
            onPick = {
                picked = it
                typing = false
            },
            onDismiss = { typing = false },
            title = stringResource(R.string.gate_question),
            range = 1..choices.last(),
        )
    }
    Spacer(Modifier.height(12.dp))
    Text(
        stringResource(R.string.gate_left, minutesText(ceilMinutes(budgetLeftMs))),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (rule.cooldownMinutes > 0) {
        Text(
            stringResource(R.string.gate_then_cooldown, minutesText(rule.cooldownMinutes)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    Spacer(Modifier.weight(1f))
    PrimaryButton(stringResource(R.string.open)) { onStart(minutes) }
    TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(stringResource(R.string.exit))
    }
}

@Composable
private fun ColumnScope.BlockedContent(
    title: String,
    countdown: String?,
    detail: String?,
    mode: Mode,
    state: AppState,
    onBypass: () -> Unit,
    onExit: () -> Unit,
) {
    Text(title, style = MaterialTheme.typography.headlineSmall)
    if (countdown != null) {
        Spacer(Modifier.height(12.dp))
        Text(countdown, style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.tertiary)
    }
    if (detail != null) {
        Spacer(Modifier.height(8.dp))
        Text(
            detail,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.tertiary,
            textAlign = TextAlign.Center,
        )
    }

    Spacer(Modifier.weight(1f))
    PrimaryButton(stringResource(R.string.exit), onClick = onExit)
    if (mode == Mode.MAX) {
        Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.mode_max),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    } else {
        Bypass(waitSeconds = Logic.bypassWaitSeconds(state), onGrant = onBypass)
    }
}

/** Standard mode escape hatch: sit through the wait, then confirm. */
@Composable
private fun Bypass(waitSeconds: Int, onGrant: () -> Unit) {
    // null until the user asks for a bypass, then the seconds still to wait.
    var remaining by remember { mutableStateOf<Int?>(null) }
    val started = remaining != null
    LaunchedEffect(started) {
        while ((remaining ?: 0) > 0) {
            delay(1000)
            remaining = remaining?.minus(1)
        }
    }

    val extra = minutesText(Logic.BYPASS_MINUTES)
    val left = remaining
    when {
        left == null -> TextButton(
            onClick = { remaining = waitSeconds },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Text(stringResource(R.string.bypass, extra))
        }
        left > 0 -> Column(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(top = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LinearProgressIndicator(
                progress = { 1f - left / waitSeconds.toFloat() },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.bypass_wait, left),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> TextButton(onClick = onGrant, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(stringResource(R.string.bypass_open, extra))
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        Text(text)
    }
}

private const val DEFAULT_SESSION_MINUTES = 5
