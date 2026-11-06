// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MuninnGuardLogTest {
    @Test fun keepsNewestFirstAndRoundTrips() {
        var s: String? = null
        s = MuninnGuardLog.add(s, MuninnGuardLog.Entry(1_000, "Gboard", false))
        s = MuninnGuardLog.add(s, MuninnGuardLog.Entry(10 * 60_000L, "SwiftKey", true))
        val list = MuninnGuardLog.parse(s)
        assertEquals(listOf("SwiftKey", "Gboard"), list.map { it.keyboard })
        assertTrue(list[0].switchedBack)
    }

    @Test fun sameTakeoverReportedTwiceIsKeptOnce() {
        val s = MuninnGuardLog.add(MuninnGuardLog.add(null, MuninnGuardLog.Entry(0, "Gboard", false)), MuninnGuardLog.Entry(30_000, "Gboard", false))
        assertEquals(1, MuninnGuardLog.parse(s).size)
    }

    @Test fun capsAtMaxAndCountsWeek() {
        var s: String? = null
        repeat(30) { s = MuninnGuardLog.add(s, MuninnGuardLog.Entry(it * 3_600_000L, "K$it", true)) }
        val list = MuninnGuardLog.parse(s)
        assertEquals(MuninnGuardLog.MAX_ENTRIES, list.size)
        assertEquals(5, MuninnGuardLog.countSince(list, 25 * 3_600_000L))
    }

    @Test fun ignoresBrokenLines() {
        assertEquals(1, MuninnGuardLog.parse("garbage\n5\t0\tGboard\n\tx").size)
    }
}
