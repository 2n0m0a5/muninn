// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import helium314.keyboard.latin.MuninnReminderTimes.Preset
import java.util.Calendar
import java.util.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuninnReminderTimesTest {
    private val tz = TimeZone.getTimeZone("Europe/Moscow")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long = Calendar.getInstance(tz).apply {
        clear()
        set(2026, Calendar.NOVEMBER, day, hour, minute, 0)
    }.timeInMillis

    @Test fun presets() {
        val now = at(6, 14, 30)
        assertEquals(at(6, 15, 30), MuninnReminderTimes.presetTime(Preset.IN_ONE_HOUR, now, tz))
        assertEquals(at(6, 19), MuninnReminderTimes.presetTime(Preset.TONIGHT, now, tz))
        assertEquals(at(7, 9), MuninnReminderTimes.presetTime(Preset.TOMORROW_MORNING, now, tz))
        assertEquals(at(13, 9), MuninnReminderTimes.presetTime(Preset.NEXT_WEEK, now, tz))
    }

    @Test fun tonightAfterSevenMeansTomorrowEvening() {
        assertEquals(at(7, 19), MuninnReminderTimes.presetTime(Preset.TONIGHT, at(6, 18, 30), tz))
        assertEquals(at(7, 19), MuninnReminderTimes.presetTime(Preset.TONIGHT, at(6, 21), tz))
    }

    @Test fun quietHoursAcrossMidnight() {
        assertTrue(MuninnReminderTimes.isQuiet(at(6, 23, 30), 23, 8, tz))
        assertTrue(MuninnReminderTimes.isQuiet(at(7, 3), 23, 8, tz))
        assertFalse(MuninnReminderTimes.isQuiet(at(7, 8), 23, 8, tz))
        assertFalse(MuninnReminderTimes.isQuiet(at(7, 22, 59), 23, 8, tz))
        assertFalse(MuninnReminderTimes.isQuiet(at(7, 3), 8, 8, tz)) // same start and end: no quiet hours
    }

    @Test fun laterSkipsQuietHours() {
        // 21:30 + 3 hours = 00:30, inside 23-8, so it moves to 8:00 the next morning
        assertEquals(at(7, 8), MuninnReminderTimes.laterTime(at(6, 21, 30), 23, 8, true, tz))
        // 14:00 + 3 hours = 17:00, fine
        assertEquals(at(6, 17), MuninnReminderTimes.laterTime(at(6, 14), 23, 8, true, tz))
        // quiet hours off: no change
        assertEquals(at(7, 0, 30), MuninnReminderTimes.laterTime(at(6, 21, 30), 23, 8, false, tz))
    }

    @Test fun comesBackAtIrregularGrowingGaps() {
        val now = at(6, 10)
        val random = java.util.Random(42)
        var previousGap = 0L
        for (shown in 1..6) {
            val next = MuninnReminderTimes.nextOccurrence(shown, now, random, 23, 8, false, tz)
            val gap = next - now
            assertTrue(gap > 0)
            assertTrue(gap > previousGap * 0.7, "gaps grow (shown=$shown)")
            previousGap = gap
        }
        // the same step gives different times: irregular
        val a = MuninnReminderTimes.nextOccurrence(1, now, java.util.Random(1), 23, 8, false, tz)
        val b = MuninnReminderTimes.nextOccurrence(1, now, java.util.Random(2), 23, 8, false, tz)
        assertTrue(a != b)
        // never inside quiet hours
        repeat(50) { seed ->
            val t = MuninnReminderTimes.nextOccurrence(1, at(6, 19), java.util.Random(seed.toLong()), 23, 8, true, tz)
            assertFalse(MuninnReminderTimes.isQuiet(t, 23, 8, tz))
        }
    }

    @Test fun dayKeyChangesAtMidnight() {
        assertEquals(MuninnReminderTimes.dayKey(at(6, 0, 1), tz), MuninnReminderTimes.dayKey(at(6, 23, 59), tz))
        assertTrue(MuninnReminderTimes.dayKey(at(7, 0, 1), tz) != MuninnReminderTimes.dayKey(at(6, 23, 59), tz))
    }
}
