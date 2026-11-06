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
    const val TONIGHT_HOUR = 19
    const val MORNING_HOUR = 9

    const val WEEKEND_HOUR = 10

    enum class Preset { IN_ONE_HOUR, TONIGHT, TOMORROW_MORNING, NEXT_WEEK, WEEKEND }

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
        // the coming Saturday at 10:00 (on a weekend that is already under way, next Saturday)
        Preset.WEEKEND -> cal(now, tz).atHour(WEEKEND_HOUR).let {
            while (it.get(Calendar.DAY_OF_WEEK) != Calendar.SATURDAY || it.timeInMillis - now < HOUR) it.add(Calendar.DAY_OF_MONTH, 1)
            it.timeInMillis
        }
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

    /** "Later": somewhere between one and four hours from now, never the same twice */
    fun laterTime(now: Long, random: java.util.Random, quietStart: Int, quietEnd: Int, quietEnabled: Boolean, tz: TimeZone = TimeZone.getDefault()) =
        outsideQuietHours(now + logUniform(random, LATER_MIN, LATER_MAX), quietStart, quietEnd, quietEnabled, tz)

    const val LATER_MIN = HOUR
    const val LATER_MAX = 4 * HOUR
    const val GAP_MIN = 20 * 60 * 1000L
    const val GAP_MAX = 36 * HOUR

    /** a length of time between [min] and [max], spread evenly over the scale (as likely 20-40 min as 18-36 h) */
    fun logUniform(random: java.util.Random, min: Long, max: Long): Long {
        val lo = kotlin.math.ln(min.toDouble())
        val hi = kotlin.math.ln(max.toDouble())
        return kotlin.math.exp(lo + random.nextDouble() * (hi - lo)).toLong()
    }

    /**
     * When a reminder comes back next. Every gap is drawn fresh and on its own, anywhere from 20 minutes to 36 hours,
     * with no rhythm and no growing pattern: like the digits of pi, nothing to learn, so it is never tuned out.
     * [timesShown] is kept for the record but does not shape the gap.
     */
    fun nextOccurrence(
        @Suppress("UNUSED_PARAMETER") timesShown: Int, now: Long, random: java.util.Random,
        quietStart: Int, quietEnd: Int, quietEnabled: Boolean, tz: TimeZone = TimeZone.getDefault()
    ): Long = outsideQuietHours(now + logUniform(random, GAP_MIN, GAP_MAX), quietStart, quietEnd, quietEnabled, tz)

    /** which ways a reminder comes back this time: the chip, the notification, or both; chosen at random each time */
    enum class Ways { CHIP, NOTIFICATION, BOTH }

    fun pickWays(random: java.util.Random): Ways = Ways.entries[random.nextInt(Ways.entries.size)]

    /** a number for the calendar day of [time], used to count reminders per day */
    fun dayKey(time: Long, tz: TimeZone = TimeZone.getDefault()): Int =
        cal(time, tz).let { it.get(Calendar.YEAR) * 1000 + it.get(Calendar.DAY_OF_YEAR) }
}
