package com.cocoonapp.core

import com.cocoonapp.data.AppState
import com.cocoonapp.data.Rule
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** What should happen when a limited app is in the foreground. */
sealed interface Gate {
    /** Not limited right now (no rule, disabled, or outside the schedule). */
    data object Free : Gate
    data class Running(val leftMs: Long) : Gate
    /** The user has to choose a session length. */
    data class Pick(val budgetLeftMs: Long) : Gate
    data class Cooldown(val until: Long) : Gate
    /** Budget used up; [until] is when the app opens up again. */
    data class Limit(val until: Long) : Gate

    val isBlocked: Boolean get() = this is Cooldown || this is Limit
}

enum class TickEvent { WARN, ENDED }

/** Pure limit rules; no Android dependencies so it can be unit tested on the JVM. */
object Logic {
    const val MINUTE = 60_000L
    const val BYPASS_MINUTES = 5
    private const val BYPASS_WAIT_BASE = 30
    private const val BYPASS_WAIT_MAX = 300
    private val SESSION_STEPS = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240)

    private fun minuteOfDay(t: ZonedDateTime) = t.hour * 60 + t.minute

    private fun atMinute(day: ZonedDateTime, minute: Int): ZonedDateTime =
        day.toLocalDate().atTime(LocalTime.of(minute / 60, minute % 60)).atZone(day.zone)

    private fun hasRange(rule: Rule) = rule.scheduleEnabled && rule.startMinute != rule.endMinute

    fun inRange(rule: Rule, now: ZonedDateTime): Boolean {
        if (!hasRange(rule)) return true
        val m = minuteOfDay(now)
        return if (rule.startMinute < rule.endMinute) m in rule.startMinute until rule.endMinute
        else m >= rule.startMinute || m < rule.endMinute
    }

    /** Start of the budget period containing [now]: the day, or the schedule range when only that counts. */
    fun periodStart(rule: Rule, now: ZonedDateTime): Long {
        val start = if (!hasRange(rule) || rule.countAllDay) {
            now.toLocalDate().atStartOfDay(now.zone)
        } else {
            // In the after-midnight part of an overnight range the period began yesterday.
            val overnightTail = rule.startMinute > rule.endMinute && minuteOfDay(now) < rule.endMinute
            atMinute(if (overnightTail) now.minusDays(1) else now, rule.startMinute)
        }
        return start.toInstant().toEpochMilli()
    }

    /** When an exhausted budget stops blocking: end of the range, or the daily reset if that comes first. */
    private fun unblockAt(rule: Rule, now: ZonedDateTime): Long {
        val midnight = now.toLocalDate().plusDays(1).atStartOfDay(now.zone)
        if (!hasRange(rule)) return midnight.toInstant().toEpochMilli()
        var end = atMinute(now, rule.endMinute)
        if (!end.isAfter(now)) end = atMinute(now.plusDays(1), rule.endMinute)
        val at = if (rule.countAllDay && midnight.isBefore(end)) midnight else end
        return at.toInstant().toEpochMilli()
    }

    private fun rolled(rule: Rule, st: AppState, now: ZonedDateTime): AppState {
        val period = periodStart(rule, now)
        return if (st.periodStart == period) st else st.copy(periodStart = period, usedMs = 0, bypassCount = 0)
    }

    private fun zoned(nowMs: Long, zone: ZoneId) = Instant.ofEpochMilli(nowMs).atZone(zone)

    fun usedMs(rule: Rule, st: AppState, nowMs: Long, zone: ZoneId): Long =
        rolled(rule, st, zoned(nowMs, zone)).usedMs

    fun status(rule: Rule, st: AppState, nowMs: Long, zone: ZoneId): Gate {
        val now = zoned(nowMs, zone)
        if (!rule.enabled || !inRange(rule, now)) return Gate.Free
        val s = rolled(rule, st, now)
        if (s.sessionLeftMs > 0) return Gate.Running(s.sessionLeftMs)
        if (nowMs < s.cooldownUntil) return Gate.Cooldown(s.cooldownUntil)
        val left = rule.maxMinutes * MINUTE - s.usedMs
        return if (left <= 0) Gate.Limit(unblockAt(rule, now)) else Gate.Pick(left)
    }

    /** Accounts [deltaMs] of foreground time that just elapsed for the app. */
    fun tick(rule: Rule, st: AppState, nowMs: Long, deltaMs: Long, zone: ZoneId): Pair<AppState, TickEvent?> {
        if (!rule.enabled) return st to null
        val now = zoned(nowMs, zone)
        val s = rolled(rule, st, now)
        if (!inRange(rule, now)) {
            return (if (rule.countAllDay) s.copy(usedMs = s.usedMs + deltaMs) else s) to null
        }
        // In range without a session the app sits behind the gate: nothing to count.
        if (s.sessionLeftMs <= 0) return s to null

        val left = (s.sessionLeftMs - deltaMs).coerceAtLeast(0)
        val used = s.usedMs + deltaMs
        if (left == 0L) {
            val ended = s.copy(
                usedMs = used,
                sessionLeftMs = 0,
                sessionTotalMs = 0,
                warned = false,
                cooldownUntil = nowMs + rule.cooldownMinutes * MINUTE,
            )
            return ended to TickEvent.ENDED
        }
        val warnMs = rule.warnMinutes * MINUTE
        // No warning for sessions that start at or below the threshold.
        if (rule.warnEnabled && !s.warned && left <= warnMs && s.sessionTotalMs > warnMs) {
            return s.copy(usedMs = used, sessionLeftMs = left, warned = true) to TickEvent.WARN
        }
        return s.copy(usedMs = used, sessionLeftMs = left) to null
    }

    fun startSession(rule: Rule, st: AppState, minutes: Int, bypass: Boolean, nowMs: Long, zone: ZoneId): AppState {
        val s = rolled(rule, st, zoned(nowMs, zone))
        return s.copy(
            sessionLeftMs = minutes * MINUTE,
            sessionTotalMs = minutes * MINUTE,
            warned = false,
            cooldownUntil = 0,
            bypassCount = if (bypass) s.bypassCount + 1 else s.bypassCount,
        )
    }

    /** Wait before a bypass is granted; doubles with every bypass in the same period. */
    fun bypassWaitSeconds(st: AppState): Int =
        (BYPASS_WAIT_BASE.toLong() shl st.bypassCount.coerceIn(0, 10)).coerceAtMost(BYPASS_WAIT_MAX.toLong()).toInt()

    /** Session lengths (minutes) offered for the remaining budget; the last one uses all of it. */
    fun sessionChoices(budgetLeftMs: Long): List<Int> {
        val max = ((budgetLeftMs + MINUTE - 1) / MINUTE).toInt().coerceAtLeast(1)
        return SESSION_STEPS.filter { it < max } + max
    }
}
