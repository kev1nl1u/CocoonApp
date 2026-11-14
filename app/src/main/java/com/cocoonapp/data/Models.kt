package com.cocoonapp.data

enum class Mode { STANDARD, MAX }

/** Per-app limit configuration. Times of day are minutes since midnight. */
data class Rule(
    val pkg: String,
    val enabled: Boolean = true,
    val maxMinutes: Int = 60,
    /** Sessions always last [sessionMinutes] instead of being chosen every time the app is opened. */
    val sessionFixed: Boolean = false,
    val sessionMinutes: Int = 10,
    val cooldownMinutes: Int = 30,
    val scheduleEnabled: Boolean = false,
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 18 * 60,
    /** With a schedule: count usage of the whole day against the budget, not just usage inside the range. */
    val countAllDay: Boolean = false,
    val warnEnabled: Boolean = true,
    val warnMinutes: Int = 2,
)

/** Per-app runtime state. */
data class AppState(
    /** Start of the budget period [usedMs] belongs to. */
    val periodStart: Long = 0,
    val usedMs: Long = 0,
    /** Foreground time left in the current session; 0 = no session. */
    val sessionLeftMs: Long = 0,
    val sessionTotalMs: Long = 0,
    val cooldownUntil: Long = 0,
    val warned: Boolean = false,
    val bypassCount: Int = 0,
)
