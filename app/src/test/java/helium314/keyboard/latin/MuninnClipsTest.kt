// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuninnClipsTest {
    @Test fun recognisesOneTimeCodes() {
        listOf("482913", " 482913 ", "48291375", "482 913", "482-913", "G-482913", "AB-4829").forEach {
            assertTrue(MuninnClips.isOneTimeCode(it), "should be a code: '$it'")
        }
    }

    @Test fun leavesOrdinaryNumbersAlone() {
        listOf("2026", "15000", "12345", "+7 916 555 01 23", "4111 1111 1111 1111", "123456789",
            "DE89 3704 0044 0532 0130 00", "ul. Pushkina 12", "482913 is your code", "", "abc-def").forEach {
            assertFalse(MuninnClips.isOneTimeCode(it), "should not be a code: '$it'")
        }
    }

    @Test fun recognisesLinks() {
        assertTrue(MuninnClips.isLink("https://youtu.be/abc"))
        assertTrue(MuninnClips.isLink("www.example.com/page"))
        assertFalse(MuninnClips.isLink("see https://youtu.be/abc"))
        assertFalse(MuninnClips.isLink(null))
    }
}
