package com.cocoonapp.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** In-memory source of truth backed by SharedPreferences. Main thread only. */
object Store {
    private lateinit var prefs: SharedPreferences

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules

    private val _states = MutableStateFlow<Map<String, AppState>>(emptyMap())
    val states: StateFlow<Map<String, AppState>> = _states

    private val _mode = MutableStateFlow(Mode.STANDARD)
    val mode: StateFlow<Mode> = _mode

    private val _hyperOsDone = MutableStateFlow(false)

    /** The user has been through the HyperOS setup card. */
    val hyperOsDone: StateFlow<Boolean> = _hyperOsDone

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("cocoon", Context.MODE_PRIVATE)
        _mode.value = runCatching { Mode.valueOf(prefs.getString(KEY_MODE, null).orEmpty()) }.getOrDefault(Mode.STANDARD)
        _hyperOsDone.value = prefs.getBoolean(KEY_HYPEROS_DONE, false)
        _rules.value = runCatching {
            val array = JSONArray(prefs.getString(KEY_RULES, "[]"))
            List(array.length()) { ruleFrom(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
        _states.value = runCatching {
            val json = JSONObject(prefs.getString(KEY_STATES, "{}").orEmpty())
            json.keys().asSequence().associateWith { stateFrom(json.getJSONObject(it)) }
        }.getOrDefault(emptyMap())
    }

    fun rule(pkg: String): Rule? = _rules.value.firstOrNull { it.pkg == pkg }

    fun state(pkg: String): AppState = _states.value[pkg] ?: AppState()

    fun saveRule(rule: Rule) {
        val current = _rules.value
        _rules.value = if (current.any { it.pkg == rule.pkg }) current.map { if (it.pkg == rule.pkg) rule else it }
        else current + rule
        persistRules()
    }

    fun deleteRule(pkg: String) {
        _rules.value = _rules.value.filterNot { it.pkg == pkg }
        _states.value = _states.value - pkg
        persistRules()
        flush()
    }

    /** [persist] = false keeps the change in memory only; call [flush] later. */
    fun setState(pkg: String, state: AppState, persist: Boolean = true) {
        if (_states.value[pkg] != state) _states.value = _states.value + (pkg to state)
        if (persist) flush()
    }

    fun update(pkg: String, transform: (Rule, AppState) -> AppState) {
        val rule = rule(pkg) ?: return
        setState(pkg, transform(rule, state(pkg)))
    }

    fun setMode(mode: Mode) {
        _mode.value = mode
        prefs.edit().putString(KEY_MODE, mode.name).apply()
    }

    fun setHyperOsDone() {
        _hyperOsDone.value = true
        prefs.edit().putBoolean(KEY_HYPEROS_DONE, true).apply()
    }

    fun flush() {
        val json = JSONObject()
        _states.value.forEach { (pkg, state) -> json.put(pkg, state.toJson()) }
        prefs.edit().putString(KEY_STATES, json.toString()).apply()
    }

    private fun persistRules() {
        val array = JSONArray()
        _rules.value.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    private fun Rule.toJson() = JSONObject()
        .put("pkg", pkg)
        .put("enabled", enabled)
        .put("max", maxMinutes)
        .put("sessionFixed", sessionFixed)
        .put("session", sessionMinutes)
        .put("cooldown", cooldownMinutes)
        .put("schedule", scheduleEnabled)
        .put("start", startMinute)
        .put("end", endMinute)
        .put("allDay", countAllDay)
        .put("warn", warnEnabled)
        .put("warnMin", warnMinutes)

    private fun ruleFrom(o: JSONObject): Rule {
        val d = Rule(pkg = o.getString("pkg"))
        return d.copy(
            enabled = o.optBoolean("enabled", d.enabled),
            maxMinutes = o.optInt("max", d.maxMinutes),
            sessionFixed = o.optBoolean("sessionFixed", d.sessionFixed),
            sessionMinutes = o.optInt("session", d.sessionMinutes),
            cooldownMinutes = o.optInt("cooldown", d.cooldownMinutes),
            scheduleEnabled = o.optBoolean("schedule", d.scheduleEnabled),
            startMinute = o.optInt("start", d.startMinute),
            endMinute = o.optInt("end", d.endMinute),
            countAllDay = o.optBoolean("allDay", d.countAllDay),
            warnEnabled = o.optBoolean("warn", d.warnEnabled),
            warnMinutes = o.optInt("warnMin", d.warnMinutes),
        )
    }

    private fun AppState.toJson() = JSONObject()
        .put("period", periodStart)
        .put("used", usedMs)
        .put("left", sessionLeftMs)
        .put("total", sessionTotalMs)
        .put("cooldown", cooldownUntil)
        .put("warned", warned)
        .put("bypass", bypassCount)

    private fun stateFrom(o: JSONObject) = AppState(
        periodStart = o.optLong("period"),
        usedMs = o.optLong("used"),
        sessionLeftMs = o.optLong("left"),
        sessionTotalMs = o.optLong("total"),
        cooldownUntil = o.optLong("cooldown"),
        warned = o.optBoolean("warned"),
        bypassCount = o.optInt("bypass"),
    )

    private const val KEY_RULES = "rules"
    private const val KEY_STATES = "states"
    private const val KEY_MODE = "mode"
    private const val KEY_HYPEROS_DONE = "hyperOsDone"
}
