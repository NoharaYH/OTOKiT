package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePanelOutlineTest {
    @Test fun touchingArcsTolerateRoundingAtPhoneAndTabletScales() {
        // 两台设备崩溃日志中的实际反向区间；闭合胶囊没有可展开的直线段。
        for ((top, bottom) in listOf(54.000004f to 54f, 77.00001f to 77f,
            58.000004f to 58f, 40.500004f to 40.5f)) {
            for (preferred in listOf(0f, bottom, 200f)) {
                assertEquals(top, themePanelSideEnd(top, bottom, preferred), 0f)
            }
        }
    }

    @Test fun expandedPanelRetainsItsOriginalLimits() {
        assertEquals(20f, themePanelSideEnd(20f, 100f, 10f), 0f)
        assertEquals(55f, themePanelSideEnd(20f, 100f, 55f), 0f)
        assertEquals(100f, themePanelSideEnd(20f, 100f, 120f), 0f)
    }

    @Test fun lowerBreakUsesMaximumArcTangentWithoutAnExtraStub() {
        for (density in listOf(1f, 2.75f, 3.5f)) {
            val (upper, lower) = themePanelSideGap(22f * density, 400f * density,
                32.75f * density, 55f * density, 1f)
            assertEquals(55f * density, upper, 0.001f)
            assertEquals((400f - 32.75f) * density, lower, 0.001f)
        }
    }

    @Test fun shortAndClosingPanelsNeverInvertTheirGap() {
        for (height in 0..400) for (progress in listOf(0f, 0.1f, 0.5f, 1f)) {
            val (upper, lower) = themePanelSideGap(22f, height.toFloat(), 33f, 55f, progress)
            assertTrue(upper.isFinite() && lower.isFinite())
            assertTrue(lower >= upper)
            if (progress == 0f) assertEquals(upper, lower, 0f)
        }
    }

    @Test fun scrollbarFillsNonScrollingMenusAndKeepsFieldHeightForScrollingMenus() {
        assertEquals(44f, themeScrollbarLength(250f, 44f, true), 0f)
        assertEquals(250f, themeScrollbarLength(250f, 44f, false), 0f)
        assertEquals(20f, themeScrollbarLength(20f, 44f, true), 0f)
        assertEquals(0f, themeScrollbarLength(-1f, 44f, false), 0f)
    }

    @Test fun sideCutsDirectlyAtFinalEndpointsOnceAWholeThumbFits() {
        val tooShort = themePanelSideGap(22f, 140f, 33f, 55f, 1f, 56f)
        assertEquals(tooShort.first, tooShort.second, 0f)
        val cut = themePanelSideGap(22f, 144f, 33f, 55f, 0.01f, 56f)
        assertEquals(55f, cut.first, 0f)
        assertEquals(111f, cut.second, 0f)
        assertEquals(cut, themePanelSideGap(22f, 144f, 33f, 55f, 1f, 56f))
        // 之后只有底边向下延展，上断口不会从中间向上缩回。
        assertEquals(55f, themePanelSideGap(22f, 400f, 33f, 55f, 1f, 56f).first, 0f)
    }
}
