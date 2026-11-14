package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.*
import org.junit.Assert.*
import org.junit.Test

class NoneThemeColorSelectionTest {
    private fun state() = GameSelectionState(GameLine.Mai, ThemeOptions.PLACEHOLDER_ID,
        ThemeOptions.PLACEHOLDER_ID, ThemeSelectionMode.None)

    @Test fun manualRolesAreIndependentAndSurviveWallpaperRoundTrip() {
        val controller = GameSelectionController(state())
        assertFalse(controller.selectNoneThemeColor(NoneThemeColorRole.Primary, 0xFF123456.toInt()))
        assertTrue(controller.selectNoneThemeWallpaper(false))
        val secondary = controller.state.noneThemeSecondaryColor
        assertTrue(controller.selectNoneThemeColor(NoneThemeColorRole.Primary, 0xFF123456.toInt()))
        assertEquals(secondary, controller.state.noneThemeSecondaryColor)
        assertTrue(controller.selectNoneThemeColor(NoneThemeColorRole.Secondary, 0xFFABCDEF.toInt()))
        controller.selectNoneThemeWallpaper(true)
        controller.updateWallpaperSeed(0xFF009900.toInt())
        controller.selectNoneThemeWallpaper(false)
        assertEquals(0xFF123456.toInt(), controller.state.noneThemePrimaryColor)
        assertEquals(0xFFABCDEF.toInt(), controller.state.noneThemeSecondaryColor)
        assertFalse(controller.selectNoneThemeColor(NoneThemeColorRole.Primary, 0x80123456.toInt()))
        assertFalse(controller.selectNoneThemeColor(NoneThemeColorRole.Primary, 0xFF123456.toInt()))
        controller.selectThemeMode(ThemeSelectionMode.Merged)
        assertFalse(controller.selectNoneThemeWallpaper(true))
        assertFalse(controller.selectNoneThemeColor(NoneThemeColorRole.Primary, 0xFF000000.toInt()))
    }

    @Test fun restoreMergesEachManualFieldAndRejectsTransparentValues() {
        val prefs = GameSelectionFormat.encode(state().copy(noneThemeUseWallpaper = false,
            noneThemePrimaryColor = 0xFF123456.toInt(), noneThemeSecondaryColor = 0xFFABCDEF.toInt()))
        val saved = prefs.copy(noneThemePrimaryColor = 0x123456, noneThemeSecondaryColor = 0xFF112233.toInt())
        val restored = restoreGameSelection(saved, prefs, ThemeRegistry::findById, ThemeRegistry::defaultFor)
        assertFalse(restored.noneThemeUseWallpaper)
        assertEquals(0xFF123456.toInt(), restored.noneThemePrimaryColor)
        assertEquals(0xFF112233.toInt(), restored.noneThemeSecondaryColor)
    }

    @Test fun manualPaletteUsesExactColorsAndBackgroundFollowsOnlyPrimary() {
        val palette = NoneThemePalette.resolve(0xFF000000.toInt(), false, 0xFF123456.toInt(), 0xFFABCDEF.toInt())
        val other = NoneThemePalette.resolve(0xFFFF0000.toInt(), false, 0xFF123456.toInt(), 0xFF112233.toInt())
        assertEquals(0xFF123456.toInt(), palette.colors.primaryColor)
        assertEquals(0xFFABCDEF.toInt(), palette.colors.secondaryColor)
        assertEquals(palette.backgroundColor, other.backgroundColor)
    }
    @Test fun secondaryOnlyWallpaperUpdatesAndMissingCandidatesClearOldAccent() {
        val controller = GameSelectionController(state())
        val primary = 0xFF162953.toInt()
        val secondary = 0xFF50B03B.toInt()
        assertTrue(controller.updateWallpaperSeed(primary, secondary))
        assertFalse(controller.updateWallpaperSeed(primary, secondary))
        assertTrue(controller.updateWallpaperSeed(primary, 0xFFDDA261.toInt()))
        val restored = restoreGameSelection(GameSelectionFormat.encode(controller.state), null,
            ThemeRegistry::findById, ThemeRegistry::defaultFor)
        assertEquals(controller.state, restored)
        assertTrue(controller.updateWallpaperSeed(primary))
        assertNull(controller.state.noneThemeWallpaperSecondary)
        assertFalse(controller.updateWallpaperSeed(primary, 0x0050B03B))
        controller.selectNoneThemeWallpaper(false)
        assertEquals(NoneThemePalette.fromSeed(primary).colors.secondaryColor, controller.state.noneThemeSecondaryColor)
    }
}
