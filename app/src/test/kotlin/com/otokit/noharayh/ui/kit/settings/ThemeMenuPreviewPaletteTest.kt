package com.otokit.noharayh.ui.kit.settings

import com.materialkolor.hct.Hct
import org.junit.Assert.*
import org.junit.Test

class ThemeMenuPreviewPaletteTest {
    @Test fun lightPrimariesKeepTheirExactMainColorAndDeriveDarkerBlocks() {
        for (seed in listOf(0xFFFFFFFF.toInt(), 0xFFD6AEFF.toInt(), 0xFFFDFA78.toInt())) {
            val colors = ThemeMenuPreviewPalette.fromPrimary(seed)
            assertEquals(seed, colors.primary)
            assertTrue(Hct.fromInt(colors.variant).tone < Hct.fromInt(seed).tone)
            assertTrue(Hct.fromInt(colors.accent).tone < Hct.fromInt(colors.variant).tone)
        }
    }

    @Test fun darkAndNeutralPrimariesRemainDistinctInsteadOfClampingBothVariantsToBlack() {
        for (seed in listOf(0xFF000000.toInt(), 0xFF141414.toInt(), 0xFF142964.toInt())) {
            val colors = ThemeMenuPreviewPalette.fromPrimary(seed)
            assertEquals(seed, colors.primary)
            assertTrue(Hct.fromInt(colors.variant).tone > Hct.fromInt(seed).tone)
            assertTrue(Hct.fromInt(colors.accent).tone > Hct.fromInt(colors.variant).tone)
            assertNotEquals(seed, colors.underpaint)
            assertEquals(255, colors.underpaint ushr 24)
        }
    }

    @Test fun midToneHasBothALighterAndADarkerBlockAndUnderpaintRetainsTheThemeTint() {
        val primary = 0xFF888888.toInt()
        val colors = ThemeMenuPreviewPalette.fromPrimary(primary)
        assertTrue(Hct.fromInt(colors.variant).tone < Hct.fromInt(primary).tone)
        assertTrue(Hct.fromInt(colors.accent).tone > Hct.fromInt(primary).tone)
        val red = ThemeMenuPreviewPalette.fromPrimary(0xFFFF0000.toInt()).underpaint
        val blue = ThemeMenuPreviewPalette.fromPrimary(0xFF0000FF.toInt()).underpaint
        assertTrue((red ushr 16 and 255) > (red and 255))
        assertTrue((blue and 255) > (blue ushr 16 and 255))
        assertEquals(colors.primary, previewColorBetween(colors.primary, colors.underpaint, 0f))
        assertEquals(colors.underpaint, previewColorBetween(colors.primary, colors.underpaint, 1f))
    }
}
