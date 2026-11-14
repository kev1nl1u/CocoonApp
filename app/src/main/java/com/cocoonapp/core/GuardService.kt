package com.cocoonapp.core

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat
import com.cocoonapp.data.Store
import com.cocoonapp.ui.GateActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId

/**
 * Watches which app is in the foreground, charges its usage against the rule
 * and puts [GateActivity] in front of it whenever it is not allowed to run.
 */
class GuardService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    /** Foreground package; null while the screen is off. */
    private var current: String? = null
    private var lastTick = 0L
    private var ticks = 0
    private var ticking = false
    private var gatePkg: String? = null
    private var gateAt = 0L

    /** What the "time left" notification shows: package and whole minutes; null = nothing. */
    private var shown: Pair<String, Int>? = null

    /** Packages whose windows float over the real foreground app. */
    private var overlays = emptySet<String>()

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            if (ticking) handler.postDelayed(this, TICK_MS)
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) switchTo(null) else resync()
        }
    }

    override fun onServiceConnected() {
        val imes = getSystemService(InputMethodManager::class.java).enabledInputMethodList.map { it.packageName }
        overlays = imes.toSet() + setOf("android", "com.android.systemui")
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(this, screenReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        instance = this
        _running.value = true
        resync()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in overlays) switchTo(pkg)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        _running.value = false
        handler.removeCallbacksAndMessages(null)
        switchTo(null)
        runCatching { unregisterReceiver(screenReceiver) }
        return super.onUnbind(intent)
    }

    /** Picks the foreground app up again after the screen comes back or the service starts. */
    private fun resync() {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val pkg = runCatching { rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        if (pkg !in overlays) switchTo(pkg)
    }

    private fun switchTo(pkg: String?) {
        if (pkg == current) return
        // Charge the tail of the app being left.
        tick(leaving = true)
        current = pkg
        lastTick = SystemClock.elapsedRealtime()

        val tracked = pkg != null && Store.rule(pkg)?.enabled == true
        handler.removeCallbacks(ticker)
        ticking = tracked
        if (tracked) {
            guard(pkg!!, opening = true)
            handler.postDelayed(ticker, TICK_MS)
        }
        syncStatus()
    }

    private fun tick(leaving: Boolean = false) {
        val pkg = current ?: return
        val rule = Store.rule(pkg)?.takeIf { it.enabled } ?: return
        val elapsed = SystemClock.elapsedRealtime()
        val delta = (elapsed - lastTick).coerceIn(0, MAX_DELTA_MS)
        lastTick = elapsed

        val now = System.currentTimeMillis()
        val (state, event) = Logic.tick(rule, Store.state(pkg), now, delta, ZoneId.systemDefault())
        // Disk writes are throttled; anything that matters is written right away.
        Store.setState(pkg, state, persist = leaving || event != null || ++ticks % PERSIST_EVERY == 0)
        when (event) {
            TickEvent.WARN -> Notifier.warn(this, pkg, state.sessionLeftMs)
            TickEvent.ENDED -> Notifier.cancel(this, pkg)
            null -> Unit
        }
        if (!leaving) {
            guard(pkg)
            syncStatus()
        }
    }

    /** Keeps the "time left" notification in step with the session of the foreground app. */
    private fun syncStatus() {
        val pkg = current
        val rule = pkg?.let { Store.rule(it) }
        val left = if (pkg == null || rule == null) {
            null
        } else {
            val gate = Logic.status(rule, Store.state(pkg), System.currentTimeMillis(), ZoneId.systemDefault())
            (gate as? Gate.Running)?.leftMs
        }
        // Reposted only when the minute changes.
        val next = if (pkg != null && left != null) pkg to Notifier.minutes(left) else null
        if (next == shown) return
        shown = next
        if (pkg != null && left != null) Notifier.status(this, pkg, left) else Notifier.clearStatus(this)
    }

    /** Shows the gate if [pkg] may not be used right now. [opening] = the app has just come to the foreground. */
    private fun guard(pkg: String, opening: Boolean = false) {
        val rule = Store.rule(pkg) ?: return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val gate = Logic.status(rule, Store.state(pkg), now, zone)
        if (gate is Gate.Free || gate is Gate.Running) return

        // A fixed session starts by itself on opening. Anywhere else (a session that just ended
        // without a break, a range that just began) the gate asks first.
        val fixed = (gate as? Gate.Pick)?.let { Logic.fixedSession(rule, it.budgetLeftMs) }
        if (opening && fixed != null) {
            Store.update(pkg) { r, s -> Logic.startSession(r, s, fixed, bypass = false, now, zone) }
            return
        }

        val elapsed = SystemClock.elapsedRealtime()
        if (pkg == gatePkg && elapsed - gateAt < GATE_DEBOUNCE_MS) return
        gatePkg = pkg
        gateAt = elapsed
        startActivity(GateActivity.intent(this, pkg))
    }

    companion object {
        private const val TICK_MS = 1000L
        private const val MAX_DELTA_MS = 5000L
        private const val PERSIST_EVERY = 15
        private const val GATE_DEBOUNCE_MS = 1000L
        private const val RESYNC_DELAY_MS = 600L

        private var instance: GuardService? = null

        private val _running = MutableStateFlow(false)

        /** The system has the service connected, which "switched on in settings" does not guarantee. */
        val running: StateFlow<Boolean> = _running

        /**
         * Called when the gate goes away. The app underneath normally announces itself
         * with a window event; this covers the case where it does not.
         */
        fun resyncSoon() {
            val service = instance ?: return
            service.handler.postDelayed({ service.resync() }, RESYNC_DELAY_MS)
        }
    }
}
