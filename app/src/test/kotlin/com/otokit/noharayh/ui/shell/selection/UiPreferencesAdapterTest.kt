package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.storage.preferences.UiPreferenceRecord
import com.otokit.noharayh.storage.preferences.UiPreferences
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.NoneThemePalette
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesAdapterTest {

    /** 读取只做字段搬运，不补默认值。 */
    @Test
    fun readsRawFieldsWithoutDefaulting() {
        val fake = FakePreferences(UiPreferenceRecord(null, "chu", "mai-a", null, "merged", "shared-a"))
        val adapter = UiPreferencesAdapter(fake)

        assertEquals(
            SelectionSource(version = null, currentGame = "chu", maiThemeId = "mai-a", chuThemeId = null,
                themeMode = "merged", mergedThemeId = "shared-a"),
            adapter.read(),
        )
    }

    /** 写入使用稳定编码：版本 1、mai/chu 字符串与规范 ID。 */
    @Test
    fun writesStableEncodedSnapshot() {
        val fake = FakePreferences(UiPreferenceRecord(null, null, null, null))
        val adapter = UiPreferencesAdapter(fake)

        adapter.write(GameSelectionState(GameLine.Chu, "mai-a", "chu-a"))

        assertEquals(
            UiPreferenceRecord(version = 1, currentGame = "chu", maiThemeId = "mai-a", chuThemeId = "chu-a",
                themeMode = "independent", mergedThemeId = "placeholder",
                noneThemeSeedColor = NoneThemePalette.DEFAULT_SEED_COLOR, noneThemeTintBackground = false),
            fake.written,
        )
    }

    /** 每个模式均以一次调用写入全部字段；读回保留未使用的各模式选择。 */
    @Test
    fun roundTripsAllModesAsOneCompleteWrite() {
        for (mode in ThemeSelectionMode.entries) {
            val fake = FakePreferences(UiPreferenceRecord(null, null, null, null))
            val adapter = UiPreferencesAdapter(fake)
            val state = GameSelectionState(GameLine.Mai, "mai-a", "chu-a", mode, "chu-shared",
                noneThemeSeedColor = 0xFF008577.toInt(), noneThemeTintBackground = true)
            adapter.write(state)
            assertEquals(1, fake.writeCount)
            assertEquals(GameSelectionFormat.encode(state), adapter.read())
        }
    }

    @Test
    fun oldPreferencesKeepBothRenderingOptionsEnabled() {
        val adapter = UiPreferencesAdapter(FakePreferences(UiPreferenceRecord(null, null, null, null)))
        assertEquals(BackgroundRenderingOptions(), adapter.readBackgroundRendering())
    }

    @Test
    fun renderingAndThemeWritesPreserveEachOtherForEveryCombination() {
        for (hardware in listOf(false, true)) for (animations in listOf(false, true)) {
            val fake = FakePreferences(UiPreferenceRecord(null, null, null, null))
            val adapter = UiPreferencesAdapter(fake)
            val theme = GameSelectionState(GameLine.Chu, "mai-a", "chu-a", ThemeSelectionMode.Merged, "shared")
            val options = BackgroundRenderingOptions(hardware, animations)
            adapter.write(theme)
            adapter.writeBackgroundRendering(options)
            assertEquals(GameSelectionFormat.encode(theme), adapter.read())
            assertEquals(options, adapter.readBackgroundRendering())
            adapter.write(theme.copy(currentGame = GameLine.Mai))
            assertEquals(options, adapter.readBackgroundRendering())
        }
    }

    @Test
    fun missingRenderingFieldDoesNotResetTheOtherField() {
        val fake = FakePreferences(UiPreferenceRecord(null, null, null, null,
            backgroundHtmlAnimations = false))
        assertEquals(BackgroundRenderingOptions(true, false), UiPreferencesAdapter(fake).readBackgroundRendering())
    }

    private class FakePreferences(var record: UiPreferenceRecord) : UiPreferences {

        var written: UiPreferenceRecord? = null
        var writeCount = 0

        override fun readSelection(): UiPreferenceRecord = record

        override fun writeSelection(
            version: Int,
            currentGame: String,
            maiThemeId: String,
            chuThemeId: String,
            themeMode: String,
            mergedThemeId: String,
            noneThemeSeedColor: Int,
            noneThemeTintBackground: Boolean,
        ) {
            writeCount++
            written = record.copy(version = version, currentGame = currentGame, maiThemeId = maiThemeId,
                chuThemeId = chuThemeId, themeMode = themeMode, mergedThemeId = mergedThemeId,
                noneThemeSeedColor = noneThemeSeedColor, noneThemeTintBackground = noneThemeTintBackground)
            record = requireNotNull(written)
        }

        override fun writeBackgroundRendering(hardwareAccelerated: Boolean, htmlAnimations: Boolean) {
            record = record.copy(backgroundHardwareAcceleration = hardwareAccelerated,
                backgroundHtmlAnimations = htmlAnimations)
        }
    }
}
