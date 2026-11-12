package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.storage.preferences.UiPreferenceRecord
import com.otokit.noharayh.storage.preferences.UiPreferences
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.GameLine
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
                themeMode = "independent", mergedThemeId = "placeholder"),
            fake.written,
        )
    }

    /** 每个模式均以一次调用写入六字段；读回保留未使用的各模式选择。 */
    @Test
    fun roundTripsAllModesAsOneCompleteWrite() {
        for (mode in ThemeSelectionMode.entries) {
            val fake = FakePreferences(UiPreferenceRecord(null, null, null, null))
            val adapter = UiPreferencesAdapter(fake)
            val state = GameSelectionState(GameLine.Mai, "mai-a", "chu-a", mode, "chu-shared")
            adapter.write(state)
            assertEquals(1, fake.writeCount)
            assertEquals(GameSelectionFormat.encode(state), adapter.read())
        }
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
        ) {
            writeCount++
            written = UiPreferenceRecord(version, currentGame, maiThemeId, chuThemeId, themeMode, mergedThemeId)
            record = requireNotNull(written)
        }
    }
}
