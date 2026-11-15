package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePanelCornersTest {
    private fun corners(progress: Float, menuBottom: Float = 3f) =
        ThemePanelCorners.resolve(progress, 4f, 22f, menuBottom, 22f, 12f)

    @Test fun collapsedCornersMatchTheCardInsetRegardlessOfTheLastRow() {
        for (last in listOf(3f, 12f, 22f)) {
            val shape = corners(0f, last)
            assertEquals(16f, shape.top + 12f, 0f)
            assertEquals(16f, shape.bottom + 12f, 0f)
        }
    }

    @Test fun expandedBottomPreservesTheExistingLastRowAndPaddingRule() {
        for (last in listOf(3f, 12f, 22f)) {
            val shape = corners(1f, last)
            assertEquals(22f, shape.top, 0f)
            assertEquals(last + 12f, shape.bottom, 0f)
            assertEquals(34f, shape.maximumBottom, 0f)
        }
    }

    @Test fun topAndBottomInterpolateOnceAndStayInsideTheirTargets() {
        assertEquals(13f, corners(.5f).top, 0f)
        assertEquals(9.5f, corners(.5f).bottom, 0f)
        for (step in 0..100) {
            val p = step / 100f
            val shape = corners(p)
            assertTrue(shape.bottom in 4f..15f)
            assertTrue(shape.maximumBottom >= shape.bottom)
        }
        assertEquals(corners(0f), corners(-1f))
        assertEquals(corners(1f), corners(2f))
    }
}
