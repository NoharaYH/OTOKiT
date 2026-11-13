package com.otokit.noharayh.ui.theme

import com.materialkolor.hct.Hct
import com.materialkolor.contrast.Contrast
import org.junit.Assert.*
import org.junit.Test

class NoneThemePaletteTest {
    @Test
    fun chromaticSeedsProduceReadableAccentsAndSlightlyMutedLightBackgrounds() {
        val seeds = listOf(0xFF6750A4, 0xFFE53935, 0xFF43A047, 0xFF1E88E5, 0xFFFFC107)
        val backgrounds = seeds.map { seed ->
            val palette = NoneThemePalette.fromSeed(seed.toInt())
            val background = Hct.fromInt(palette.backgroundColor)
            assertEquals(88.0, background.tone, 0.5)
            assertTrue(background.chroma > 8.0 && background.chroma < 19.0)
            for (accent in listOf(palette.colors.primaryColor, palette.colors.secondaryColor)) {
                assertEquals(0xFF, accent ushr 24)
                // 两种强调色既在白容器上可辨，也能用于浅彩背景上的标题。
                val tone = Hct.fromInt(accent).tone
                assertTrue(Contrast.ratioOfTones(tone, 100.0) >= 4.5)
                assertTrue(Contrast.ratioOfTones(tone, background.tone) >= 3.0)
            }
            assertNotEquals(palette.colors.primaryColor, palette.colors.secondaryColor)
            palette.backgroundColor
        }
        assertEquals(seeds.size, backgrounds.toSet().size)
    }

    @Test
    fun extremesRemainOpaqueAndDeterministic() {
        for (seed in listOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF808080.toInt())) {
            val first = NoneThemePalette.fromSeed(seed)
            assertEquals(first, NoneThemePalette.fromSeed(seed))
            assertEquals(0xFF, first.backgroundColor ushr 24)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTransparentSeed() { NoneThemePalette.fromSeed(0x006750A4) }
}
