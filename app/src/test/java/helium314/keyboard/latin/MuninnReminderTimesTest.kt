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

    @Test fun laterIsOneToFourHoursAndSkipsQuietHours() {
        repeat(100) { seed ->
            val r = java.util.Random(seed.toLong())
            val t = MuninnReminderTimes.laterTime(at(6, 14), r, 23, 8, true, tz)
            assertTrue(t - at(6, 14) in MuninnReminderTimes.LATER_MIN..MuninnReminderTimes.LATER_MAX)
            // late in the evening, anything that falls in quiet hours moves to 8:00
            val late = MuninnReminderTimes.laterTime(at(6, 22, 30), java.util.Random(seed.toLong()), 23, 8, true, tz)
            assertFalse(MuninnReminderTimes.isQuiet(late, 23, 8, tz))
        }
    }

    @Test fun comesBackAtRandomUnpatternedTimes() {
        val now = at(6, 10)
        val random = java.util.Random(42)
        val gaps = (1..200).map { MuninnReminderTimes.nextOccurrence(it, now, random, 23, 8, false, tz) - now }
        gaps.forEach { assertTrue(it in MuninnReminderTimes.GAP_MIN..MuninnReminderTimes.GAP_MAX) }
        // spread over the whole range, no repeats, no growth with the number of times shown
        assertTrue(gaps.min() < 60 * 60 * 1000L)
        assertTrue(gaps.max() > 24 * 60 * 60 * 1000L)
        assertEquals(gaps.size, gaps.toSet().size)
        assertTrue(gaps.take(100).average() in gaps.drop(100).average() * 0.5..gaps.drop(100).average() * 2.0)
        // never inside quiet hours
        repeat(50) { seed ->
            val t = MuninnReminderTimes.nextOccurrence(1, at(6, 19), java.util.Random(seed.toLong()), 23, 8, true, tz)
            assertFalse(MuninnReminderTimes.isQuiet(t, 23, 8, tz))
        }
    }

    @Test fun waysAreChosenAtRandom() {
        val r = java.util.Random(7)
        val seen = (1..60).map { MuninnReminderTimes.pickWays(r) }.toSet()
        assertEquals(MuninnReminderTimes.Ways.entries.toSet(), seen)
    }

    @Test fun dayKeyChangesAtMidnight() {
        assertEquals(MuninnReminderTimes.dayKey(at(6, 0, 1), tz), MuninnReminderTimes.dayKey(at(6, 23, 59), tz))
        assertTrue(MuninnReminderTimes.dayKey(at(7, 0, 1), tz) != MuninnReminderTimes.dayKey(at(6, 23, 59), tz))
    }
}
