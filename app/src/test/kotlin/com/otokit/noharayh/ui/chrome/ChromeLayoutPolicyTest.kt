package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeLayoutPolicyTest {

    /** 599 仍是 Compact：只占标题行高，不占左侧。 */
    @Test
    fun compactUsesTopOnly() {
        val layoutClass = WindowBreakpointPolicy.classify(599f)

        assertEquals(WindowLayoutClass.Compact, layoutClass)
        assertFalse(ChromeLayoutPolicy.usesRail(layoutClass))
        assertEquals(
            ChromeInsets(top = 72),
            ChromeLayoutPolicy.insetsFor(layoutClass, titleHeightPx = 72, railWidthPx = 88),
        )
    }

    /** 600 起进入 Rail；839/840 两侧都保持 Rail，只占左侧宽度。 */
    @Test
    fun mediumAndExpandedUseRail() {
        listOf(600f, 839f, 840f, 1200f).forEach { widthDp ->
            val layoutClass = WindowBreakpointPolicy.classify(widthDp)

            assertTrue("width=$widthDp", ChromeLayoutPolicy.usesRail(layoutClass))
            assertEquals(
                "width=$widthDp",
                ChromeInsets(left = 88),
                ChromeLayoutPolicy.insetsFor(layoutClass, titleHeightPx = 72, railWidthPx = 88),
            )
        }
    }

    /** 非法尺寸不产生负占用。 */
    @Test
    fun clampsNegativeValues() {
        assertEquals(
            ChromeInsets(top = 0),
            ChromeLayoutPolicy.insetsFor(WindowLayoutClass.Compact, titleHeightPx = -1, railWidthPx = 88),
        )
        assertEquals(
            ChromeInsets(left = 0),
            ChromeLayoutPolicy.insetsFor(WindowLayoutClass.Medium, titleHeightPx = 72, railWidthPx = -1),
        )
    }
}
