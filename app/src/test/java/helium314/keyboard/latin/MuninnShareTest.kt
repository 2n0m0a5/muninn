// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class MuninnShareTest {
    @Test fun tiktokCaptionAndLink() {
        val s = MuninnShare.parse("10-minute stretch for people who sit all day https://vm.tiktok.com/ZM123/", null)!!
        assertEquals("10-minute stretch for people who sit all day", s.title)
        assertEquals("https://vm.tiktok.com/ZM123/", s.url)
        assertEquals("watch", MuninnShare.suggestedTag(s))
    }

    @Test fun linkOnlyUsesSubjectAsTitleAndKeepsIt() {
        val s = MuninnShare.parse("https://youtu.be/abc", "How to cook plov")!!
        assertEquals("How to cook plov", s.title)
        assertEquals("How to cook plov\nhttps://youtu.be/abc", s.text)
    }

    @Test fun plainTextHasNoLinkNoTag() {
        val s = MuninnShare.parse("Buy milk and bread", null)!!
        assertNull(s.url)
        assertNull(MuninnShare.suggestedTag(s))
        assertEquals("Buy milk and bread", s.text)
    }

    @Test fun nothingSharedIsNull() = assertNull(MuninnShare.parse("  ", null))

    @Test fun weekendIsTheComingSaturdayMorning() {
        val tz = TimeZone.getTimeZone("UTC")
        val wed = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 4, 15, 0, 0) }.timeInMillis
        val sat = Calendar.getInstance(tz).apply { timeInMillis = MuninnReminderTimes.presetTime(MuninnReminderTimes.Preset.WEEKEND, wed, tz) }
        assertEquals(Calendar.SATURDAY, sat.get(Calendar.DAY_OF_WEEK))
        assertEquals(7, sat.get(Calendar.DAY_OF_MONTH))
        assertEquals(10, sat.get(Calendar.HOUR_OF_DAY))
        // on Saturday afternoon it is next week's Saturday
        val satNoon = Calendar.getInstance(tz).apply { set(2026, Calendar.NOVEMBER, 7, 12, 0, 0) }.timeInMillis
        val next = Calendar.getInstance(tz).apply { timeInMillis = MuninnReminderTimes.presetTime(MuninnReminderTimes.Preset.WEEKEND, satNoon, tz) }
        assertEquals(14, next.get(Calendar.DAY_OF_MONTH))
    }
}
