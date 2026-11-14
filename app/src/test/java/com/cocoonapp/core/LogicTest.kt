package com.cocoonapp.core

import com.cocoonapp.data.AppState
import com.cocoonapp.data.Rule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class LogicTest {
    private val zone = ZoneId.of("Europe/Rome")
    private val min = Logic.MINUTE

    private fun at(h: Int, m: Int = 0, day: Int = 14): ZonedDateTime =
        ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone)

    private fun ms(h: Int, m: Int = 0, day: Int = 14) = at(h, m, day).toInstant().toEpochMilli()

    private val rule = Rule(pkg = "x", maxMinutes = 60, cooldownMinutes = 30, warnMinutes = 2)
    private val day = rule.copy(scheduleEnabled = true, startMinute = 9 * 60, endMinute = 18 * 60)
    private val night = rule.copy(scheduleEnabled = true, startMinute = 22 * 60, endMinute = 6 * 60)

    /** Runs a started session for [minutes] of foreground time, one second per tick. */
    private fun use(r: Rule, st: AppState, startMs: Long, minutes: Int): Triple<AppState, Long, List<TickEvent>> {
        var s = st
        var now = startMs
        val events = mutableListOf<TickEvent>()
        repeat(minutes * 60) {
            now += 1000
            val (next, ev) = Logic.tick(r, s, now, 1000, zone)
            s = next
            ev?.let(events::add)
        }
        return Triple(s, now, events)
    }

    @Test
    fun rangeWithinDay() {
        assertTrue(Logic.inRange(rule, at(3)))
        assertTrue(Logic.inRange(day, at(9)))
        assertTrue(Logic.inRange(day, at(17, 59)))
        assertFalse(Logic.inRange(day, at(18)))
        assertFalse(Logic.inRange(day, at(8, 59)))
    }

    @Test
    fun rangeOvernight() {
        assertTrue(Logic.inRange(night, at(23)))
        assertTrue(Logic.inRange(night, at(3)))
        assertFalse(Logic.inRange(night, at(6)))
        assertFalse(Logic.inRange(night, at(12)))
    }

    @Test
    fun periodStarts() {
        assertEquals(ms(0), Logic.periodStart(rule, at(15)))
        assertEquals(ms(9), Logic.periodStart(day, at(15)))
        assertEquals(ms(0), Logic.periodStart(day.copy(countAllDay = true), at(15)))
        assertEquals(ms(22), Logic.periodStart(night, at(23)))
        assertEquals(ms(22), Logic.periodStart(night, at(1, 0, day = 15)))
    }

    @Test
    fun freshAppAsksForSession() {
        assertEquals(Gate.Pick(60 * min), Logic.status(rule, AppState(), ms(10), zone))
    }

    @Test
    fun sessionRunsThenCoolsDownThenAsksAgain() {
        val start = ms(10)
        val started = Logic.startSession(rule, AppState(), 10, false, start, zone)
        assertEquals(Gate.Running(10 * min), Logic.status(rule, started, start, zone))

        val (ended, end, events) = use(rule, started, start, 10)
        assertEquals(listOf(TickEvent.WARN, TickEvent.ENDED), events)
        assertEquals(Gate.Cooldown(end + 30 * min), Logic.status(rule, ended, end, zone))
        assertEquals(Gate.Pick(50 * min), Logic.status(rule, ended, end + 30 * min, zone))
    }

    @Test
    fun pausedSessionKeepsItsTime() {
        val start = ms(10)
        val started = Logic.startSession(rule, AppState(), 10, false, start, zone)
        val (paused, _, _) = use(rule, started, start, 4)
        // Two hours away from the app: nothing is ticked, so the session is untouched.
        assertEquals(Gate.Running(6 * min), Logic.status(rule, paused, ms(12, 4), zone))
    }

    @Test
    fun warnsOnceAtThreshold() {
        val start = ms(10)
        val started = Logic.startSession(rule, AppState(), 10, false, start, zone)
        val (s, _, events) = use(rule, started, start, 9)
        assertEquals(listOf(TickEvent.WARN), events)
        assertTrue(s.warned)
    }

    @Test
    fun noWarningForShortSessionOrWhenDisabled() {
        val start = ms(10)
        val short = Logic.startSession(rule, AppState(), 2, false, start, zone)
        assertEquals(listOf(TickEvent.ENDED), use(rule, short, start, 2).third)

        val silent = rule.copy(warnEnabled = false)
        val long = Logic.startSession(silent, AppState(), 10, false, start, zone)
        assertEquals(listOf(TickEvent.ENDED), use(silent, long, start, 10).third)
    }

    @Test
    fun budgetExhaustedBlocksUntilNextDay() {
        val r = rule.copy(cooldownMinutes = 0)
        val start = ms(10)
        val (ended, end, _) = use(r, Logic.startSession(r, AppState(), 60, false, start, zone), start, 60)
        assertEquals(Gate.Limit(ms(0, 0, day = 15)), Logic.status(r, ended, end, zone))
        assertEquals(Gate.Pick(60 * min), Logic.status(r, ended, ms(0, 1, day = 15), zone))
    }

    @Test
    fun limitInsideRangeLiftsAtRangeEnd() {
        val r = day.copy(cooldownMinutes = 0)
        val start = ms(10)
        val (ended, end, _) = use(r, Logic.startSession(r, AppState(), 60, false, start, zone), start, 60)
        assertEquals(Gate.Limit(ms(18)), Logic.status(r, ended, end, zone))
        assertEquals(Gate.Free, Logic.status(r, ended, ms(18), zone))
    }

    @Test
    fun outsideRangeIsFreeAndCountsOnlyIfAllDay() {
        assertEquals(Gate.Free, Logic.status(day, AppState(), ms(7), zone))

        val (rangeOnly, _) = Logic.tick(day, AppState(), ms(7), 20 * min, zone)
        assertEquals(Gate.Pick(60 * min), Logic.status(day, rangeOnly, ms(10), zone))

        val allDay = day.copy(countAllDay = true)
        val (counted, ev) = Logic.tick(allDay, AppState(), ms(7), 20 * min, zone)
        assertNull(ev)
        assertEquals(Gate.Pick(40 * min), Logic.status(allDay, counted, ms(10), zone))
    }

    @Test
    fun overnightBudgetSpansMidnight() {
        val r = night.copy(cooldownMinutes = 0)
        val start = ms(23)
        val (s, _, _) = use(r, Logic.startSession(r, AppState(), 20, false, start, zone), start, 20)
        assertEquals(Gate.Pick(40 * min), Logic.status(r, s, ms(1, 0, day = 15), zone))
        // Next night starts with a fresh budget.
        assertEquals(Gate.Pick(60 * min), Logic.status(r, s, ms(22, 0, day = 15), zone))
    }

    @Test
    fun disabledRuleIsFreeAndUncounted() {
        val off = rule.copy(enabled = false)
        assertEquals(Gate.Free, Logic.status(off, AppState(), ms(10), zone))
        assertEquals(AppState(), Logic.tick(off, AppState(), ms(10), min, zone).first)
    }

    @Test
    fun bypassOpensBlockedAppAndWaitEscalates() {
        val start = ms(10)
        val (cooling, end, _) = use(rule, Logic.startSession(rule, AppState(), 5, false, start, zone), start, 5)
        assertTrue(Logic.status(rule, cooling, end, zone).isBlocked)
        assertEquals(30, Logic.bypassWaitSeconds(cooling))

        val bypassed = Logic.startSession(rule, cooling, Logic.BYPASS_MINUTES, true, end, zone)
        assertEquals(Gate.Running(5 * min), Logic.status(rule, bypassed, end, zone))
        assertEquals(60, Logic.bypassWaitSeconds(bypassed))
        assertEquals(300, Logic.bypassWaitSeconds(bypassed.copy(bypassCount = 9)))

        val (again, end2, _) = use(rule, bypassed, end, 5)
        assertEquals(Gate.Cooldown(end2 + 30 * min), Logic.status(rule, again, end2, zone))
    }

    @Test
    fun sessionChoicesEndWithWholeBudget() {
        assertEquals(listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60), Logic.sessionChoices(60 * min))
        assertEquals(listOf(1, 2, 3, 5, 7), Logic.sessionChoices(7 * min - 20_000))
        assertEquals(listOf(1), Logic.sessionChoices(30_000))
    }

    @Test
    fun fixedSessionIsCutToTheBudgetLeft() {
        assertNull(Logic.fixedSession(rule, 60 * min))
        val fixed = rule.copy(sessionFixed = true, sessionMinutes = 10)
        assertEquals(10, Logic.fixedSession(fixed, 60 * min))
        assertEquals(4, Logic.fixedSession(fixed, 4 * min - 20_000))
        assertEquals(1, Logic.fixedSession(fixed.copy(sessionMinutes = 0), 60 * min))
    }
}
