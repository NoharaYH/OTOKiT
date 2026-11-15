package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.*
import org.junit.Test

class ThemeMenuPreviewPaletteTest {
    @Test fun allPrimaryTonesKeepTheirOriginalValueAndHaveOpaqueLightUnderpaint() {
        for (seed in listOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFF142964.toInt(), 0xFF888888.toInt(), 0xFFD6AEFF.toInt())) {
            val colors = ThemeMenuPreviewPalette.fromPrimary(seed)
            assertEquals(seed, colors.primary)
            assertEquals(255, colors.underpaint ushr 24)
            for (shift in listOf(0, 8, 16)) assertTrue((colors.underpaint ushr shift and 255) >= 210)
        }
    }
    @Test fun waitingAndFoldedColorsRetainTheThemeTint() {
        val red = ThemeMenuPreviewPalette.fromPrimary(0xFFFF0000.toInt()).underpaint
        val blue = ThemeMenuPreviewPalette.fromPrimary(0xFF0000FF.toInt()).underpaint
        assertTrue((red ushr 16 and 255) > (red and 255))
        assertTrue((blue and 255) > (blue ushr 16 and 255))
        assertNotEquals(red, blue)
    }
    @Test fun selectedSurfaceAndStrokeBlendingPreserveEndpointsAndClampOvershoot() {
        val from = 0x00E0E0E0
        val to = 0x66323232
        assertEquals(from, previewColorBetween(from, to, -1f))
        assertEquals(to, previewColorBetween(from, to, 2f))
        assertEquals(0x33, previewColorBetween(from, to, 0.5f) ushr 24)
    }
}
