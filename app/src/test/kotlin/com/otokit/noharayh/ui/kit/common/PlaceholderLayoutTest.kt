package com.otokit.noharayh.ui.kit.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceholderLayoutTest {

    /** 高度固定为 160dp 的三分之一，按密度取整。 */
    @Test
    fun buttonHeightIsThirdOfOldHeight() {
        assertEquals(160, PlaceholderLayout.buttonHeightPx(3f))
        assertEquals(53, PlaceholderLayout.buttonHeightPx(1f))
    }

    /** 窗口宽度足够时取 80% 窗口；面板过窄时夹到可用宽度。 */
    @Test
    fun buttonWidthUsesWindowThenClampsToPanel() {
        assertEquals(960, PlaceholderLayout.buttonWidthPx(1200, 2000, 3f))
        val clamped = PlaceholderLayout.buttonWidthPx(1200, 600, 3f)
        assertTrue(clamped < 960)
        assertTrue(clamped >= 1)
    }

    /** 没有窗口宽度时退回面板比例，仍不越过内边距。 */
    @Test
    fun buttonWidthFallsBackToPanelWhenWindowUnknown() {
        val width = PlaceholderLayout.buttonWidthPx(0, 1000, 2f)
        assertTrue(width <= 800)
        assertTrue(width >= 1)
    }
}
