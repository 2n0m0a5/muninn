// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import java.util.Calendar
import java.util.TimeZone

/**
 * Muninn: the time rules for reminders, kept free of Android so they can be unit-tested.
 * All times are milliseconds since 1970; [tz] is the phone's time zone.
 */
object MuninnReminderTimes {
    const val HOUR = 60 * 60 * 1000L
    /** "Later" brings a reminder back this much later */
    const val LATER_DELAY = 3 * HOUR
    const val TONIGHT_HOUR = 19
    const val MORNING_HOUR = 9

    enum class Preset { IN_ONE_HOUR, TONIGHT, TOMORROW_MORNING, NEXT_WEEK }

    private fun cal(now: Long, tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = now }

    private fun Calendar.atHour(hour: Int) = apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun presetTime(preset: Preset, now: Long, tz: TimeZone = TimeZone.getDefault()): Long = when (preset) {
        Preset.IN_ONE_HOUR -> now + HOUR
        // tonight at 19:00; if that has passed (or is under an hour away), tomorrow at 19:00
        Preset.TONIGHT -> cal(now, tz).atHour(TONIGHT_HOUR).let {
            if (it.timeInMillis - now < HOUR) it.add(Calendar.DAY_OF_MONTH, 1)
            it.timeInMillis
        }
        Preset.TOMORROW_MORNING -> cal(now, tz).apply { add(Calendar.DAY_OF_MONTH, 1) }.atHour(MORNING_HOUR).timeInMillis
        Preset.NEXT_WEEK -> cal(now, tz).apply { add(Calendar.DAY_OF_MONTH, 7) }.atHour(MORNING_HOUR).timeInMillis
    }

    /** whether [time] falls in quiet hours [start]..[end] (hours of the day, may wrap past midnight, e.g. 23..8) */
    fun isQuiet(time: Long, start: Int, end: Int, tz: TimeZone = TimeZone.getDefault()): Boolean {
        if (start == end) return false
        val hour = cal(time, tz).get(Calendar.HOUR_OF_DAY)
        return if (start < end) hour in start until end
            else hour >= start || hour < end
    }

    /** the first moment at or after [time] that is outside quiet hours */
    fun outsideQuietHours(time: Long, start: Int, end: Int, enabled: Boolean, tz: TimeZone = TimeZone.getDefault()): Long {
        if (!enabled || !isQuiet(time, start, end, tz)) return time
        val c = cal(time, tz).atHour(end)
        if (c.timeInMillis <= time) c.add(Calendar.DAY_OF_MONTH, 1)
        return c.timeInMillis
    }

    /** when a reminder comes back after "Later" */
    fun laterTime(now: Long, quietStart: Int, quietEnd: Int, quietEnabled: Boolean, tz: TimeZone = TimeZone.getDefault()) =
        outsideQuietHours(now + LATER_DELAY, quietStart, quietEnd, quietEnabled, tz)

    /**
     * Gaps between the times a reminder comes back: it starts close together and spreads out, so it is noticed
     * without becoming noise. Each gap is shaken by up to 30% either way, so it never turns into a predictable
     * rhythm the brain learns to ignore (habituation).
     */
    private val GAPS_HOURS = longArrayOf(3, 5, 9, 24, 48, 72, 120, 168)

    /** when a reminder comes back after it was shown [timesShown] times (1 = it was just shown for the first time) */
    fun nextOccurrence(
        timesShown: Int, now: Long, random: java.util.Random,
        quietStart: Int, quietEnd: Int, quietEnabled: Boolean, tz: TimeZone = TimeZone.getDefault()
    ): Long {
        val gap = GAPS_HOURS[(timesShown - 1).coerceIn(0, GAPS_HOURS.lastIndex)] * HOUR
        val jitter = 0.7 + random.nextDouble() * 0.6 // 0.7 .. 1.3
        return outsideQuietHours(now + (gap * jitter).toLong(), quietStart, quietEnd, quietEnabled, tz)
    }

    /** a number for the calendar day of [time], used to count reminders per day */
    fun dayKey(time: Long, tz: TimeZone = TimeZone.getDefault()): Int =
        cal(time, tz).let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
}
