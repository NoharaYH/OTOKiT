package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.storage.preferences.UiPreferenceRecord
import com.otokit.noharayh.storage.preferences.UiPreferences
import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesAdapterTest {

    /** 读取只做字段搬运，不补默认值。 */
    @Test
    fun readsRawFieldsWithoutDefaulting() {
        val fake = FakePreferences(UiPreferenceRecord(null, "chu", "mai-a", null))
        val adapter = UiPreferencesAdapter(fake)

        assertEquals(
            SelectionSource(version = null, currentGame = "chu", maiThemeId = "mai-a", chuThemeId = null),
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
            UiPreferenceRecord(version = 1, currentGame = "chu", maiThemeId = "mai-a", chuThemeId = "chu-a"),
            fake.written,
        )
    }

    private class FakePreferences(var record: UiPreferenceRecord) : UiPreferences {

        var written: UiPreferenceRecord? = null

        override fun readSelection(): UiPreferenceRecord = record

        override fun writeSelection(
            version: Int,
            currentGame: String,
            maiThemeId: String,
            chuThemeId: String,
        ) {
            written = UiPreferenceRecord(version, currentGame, maiThemeId, chuThemeId)
        }
    }
}
