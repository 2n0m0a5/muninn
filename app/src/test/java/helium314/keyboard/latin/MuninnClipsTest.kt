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
            "DE89 3704 0044 0532 0130 00", "ul. Pushkina 12", "", "abc-def",
            "The code review is on 12 March 2026", "Order 12345678 has shipped", "Call me at 555-0199 about the PIN pad").forEach {
            assertFalse(MuninnClips.isOneTimeCode(it), "should not be a code: '$it'")
        }
    }

    @Test fun recognisesCodesInsideMessages() {
        listOf("482913 is your code", "Your verification code is 482913. Don't share it.",
            "Код подтверждения: 4829", "G-482913 is your Google verification code",
            "رمز التحقق الخاص بك هو 482913", "Votre code de connexion : 482 913").forEach {
            assertTrue(MuninnClips.isOneTimeCode(it), "should be a code: '$it'")
        }
    }

    @Test fun findsLinksInsideMergedText() {
        val merged = "Plov recipe\nhttps://youtu.be/plov and also www.example.com/x"
        assertFalse(MuninnClips.isLink(merged))
        assertTrue(MuninnClips.hasLink(merged))
        assertTrue(MuninnClips.linkRanges(merged).size == 2)
        assertFalse(MuninnClips.hasLink("just some text"))
    }

    @Test fun recognisesLinks() {
        assertTrue(MuninnClips.isLink("https://youtu.be/abc"))
        assertTrue(MuninnClips.isLink("www.example.com/page"))
        assertFalse(MuninnClips.isLink("see https://youtu.be/abc"))
        assertFalse(MuninnClips.isLink(null))
    }
}
