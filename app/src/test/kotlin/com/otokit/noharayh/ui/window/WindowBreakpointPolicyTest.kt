package com.otokit.noharayh.ui.window

import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy
import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WindowBreakpointPolicyTest {

    /** 600dp 与 840dp 的边界归属：小于为低一档，等于进入高一档。 */
    @Test
    fun classifiesExactBreakpointBoundaries() {
        assertEquals(WindowLayoutClass.Compact, WindowBreakpointPolicy.classify(599f))
        assertEquals(WindowLayoutClass.Compact, WindowBreakpointPolicy.classify(599.9f))
        assertEquals(WindowLayoutClass.Medium, WindowBreakpointPolicy.classify(600f))
        assertEquals(WindowLayoutClass.Medium, WindowBreakpointPolicy.classify(839f))
        assertEquals(WindowLayoutClass.Medium, WindowBreakpointPolicy.classify(839.9f))
        assertEquals(WindowLayoutClass.Expanded, WindowBreakpointPolicy.classify(840f))
    }

    /** 不同 density 下等价的可用宽度得到相同分类。 */
    @Test
    fun equivalentUsableWidthsClassifyAlike() {
        val highDensity = WindowBreakpointPolicy.stableWidthDp(
            widthPx = 1200,
            stableLeftPx = 80,
            stableRightPx = 80,
            density = 2f,
        )
        val lowDensity = WindowBreakpointPolicy.stableWidthDp(
            widthPx = 600,
            stableLeftPx = 80,
            stableRightPx = 0,
            density = 1f,
        )

        assertEquals(520f, highDensity!!, 0f)
        assertEquals(520f, lowDensity!!, 0f)
        assertEquals(WindowLayoutClass.Compact, WindowBreakpointPolicy.classify(highDensity))
        assertEquals(WindowLayoutClass.Compact, WindowBreakpointPolicy.classify(lowDensity))
    }

    /** 900px、density=1、左右各 40px 时分类宽 820dp，落到 Medium。 */
    @Test
    fun stableWidthSubtractsBothHorizontalInsets() {
        val widthDp = WindowBreakpointPolicy.stableWidthDp(900, 40, 40, 1f)

        assertEquals(820f, widthDp!!, 0f)
        assertEquals(WindowLayoutClass.Medium, WindowBreakpointPolicy.classify(widthDp))
    }

    /** 不提前取整：839.9dp 不能因 roundToInt 变成 840。 */
    @Test
    fun keepsFractionalPrecision() {
        val widthDp = WindowBreakpointPolicy.stableWidthDp(8399, 0, 0, 10f)

        assertEquals(839.9f, widthDp!!, 0.001f)
        assertEquals(WindowLayoutClass.Medium, WindowBreakpointPolicy.classify(widthDp))
    }

    /** 无效窗口或 density 不产生可分类宽度。 */
    @Test
    fun rejectsInvalidInputs() {
        assertNull(WindowBreakpointPolicy.stableWidthDp(0, 0, 0, 2f))
        assertNull(WindowBreakpointPolicy.stableWidthDp(-100, 0, 0, 2f))
        assertNull(WindowBreakpointPolicy.stableWidthDp(1080, 0, 0, 0f))
        assertNull(WindowBreakpointPolicy.stableWidthDp(1080, 0, 0, -1f))
        assertNull(WindowBreakpointPolicy.stableWidthDp(1080, 0, 0, Float.NaN))
        assertNull(WindowBreakpointPolicy.stableWidthDp(1080, 0, 0, Float.POSITIVE_INFINITY))
    }

    /** 安全区大于窗口宽度时不出现负宽度，按 0dp 归入 Compact。 */
    @Test
    fun clampsNegativeUsableWidthToZero() {
        val widthDp = WindowBreakpointPolicy.stableWidthDp(300, 200, 200, 1f)

        assertEquals(0f, widthDp!!, 0f)
        assertEquals(WindowLayoutClass.Compact, WindowBreakpointPolicy.classify(widthDp))
    }
}
