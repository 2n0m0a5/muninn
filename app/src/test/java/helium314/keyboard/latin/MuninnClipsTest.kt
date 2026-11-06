// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MuninnClipsTest {
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
