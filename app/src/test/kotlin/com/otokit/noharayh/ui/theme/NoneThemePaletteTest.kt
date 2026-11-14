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
    @Test fun mutedWallpaperDoesNotBecomeVividAndItsGoldAccentIsPreserved() {
        val seed = 0xFF716F8B.toInt()
        val gold = 0xFFDDA261.toInt()
        val palette = NoneThemePalette.fromSeed(seed, gold)
        val source = Hct.fromInt(seed)
        val primary = Hct.fromInt(palette.colors.primaryColor)
        val secondary = Hct.fromInt(palette.colors.secondaryColor)
        assertEquals(source.chroma, primary.chroma, 2.0)
        assertEquals(source.hue, primary.hue, 3.0)
        assertEquals(Hct.fromInt(gold).hue, secondary.hue, 3.0)
        assertTrue(Hct.fromInt(palette.backgroundColor).chroma < 10.0)
    }

    @Test fun publicWallpaperCandidatesProvideDistinctAccentsWithoutInventingHues() {
        val blue = 0xFF162953.toInt()
        val green = 0xFF50B03B.toInt()
        assertEquals(green, NoneThemePalette.selectWallpaperSecondary(blue, listOf(green, 0xFF63C586.toInt())))
        assertNull(NoneThemePalette.selectWallpaperSecondary(blue, listOf(blue, 0xFF808080.toInt(), 0x0000FF00)))
        assertNull(NoneThemePalette.selectWallpaperSecondary(blue, emptyList()))
    }

    @Test fun measuredWallpapersHaveReadablePrimaryAndSecondaryColors() {
        val samples = listOf(0xFF716F8B to 0xFFDDA261, 0xFF162953 to 0xFF50B03B,
            0xFF512B22 to 0xFFAF7678, 0xFF309A57 to 0xFFF7C52D, 0xFF000000 to 0xFFFFFFFF)
        for ((primary, secondary) in samples) {
            val palette = NoneThemePalette.fromSeed(primary.toInt(), secondary.toInt())
            for (color in listOf(palette.colors.primaryColor, palette.colors.secondaryColor)) {
                val tone = Hct.fromInt(color).tone
                assertTrue(Contrast.ratioOfTones(tone, 100.0) >= 4.5)
                assertTrue(Contrast.ratioOfTones(tone, Hct.fromInt(palette.backgroundColor).tone) >= 3.0)
            }
        }
    }
}
