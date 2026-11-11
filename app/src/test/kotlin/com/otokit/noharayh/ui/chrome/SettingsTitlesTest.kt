package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.settings.SettingsPalette
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import org.junit.Assert.*
import org.junit.Test

class SettingsTitlesTest {
    @Test fun rootIsGrayAndEverySectionKeepsItsOwnInk() {
        assertEquals(SettingsPalette.GRAY, TitleTexts.colorForSettings(null))
        assertEquals(SettingsPalette.SYNC, TitleTexts.colorForSettings(SettingsSection.ScoreSync))
        assertEquals(SettingsPalette.APP, TitleTexts.colorForSettings(SettingsSection.App))
        assertEquals(SettingsPalette.PERSONALIZATION, TitleTexts.colorForSettings(SettingsSection.Personalization))
        assertEquals(SettingsPalette.GRAY, TitleTexts.colorForSettings(SettingsSection.About))
    }
    @Test fun fourDirectChildrenHaveDistinctBilingualTitles() {
        val titles = SettingsSection.entries.map(TitleTexts::forSettings)
        assertEquals(4, titles.toSet().size)
        assertEquals(TitleTexts.Setting, TitleTexts.forSettings(null))
        assertEquals(R.string.settings_tile_about, TitleTexts.forSettings(SettingsSection.About).chinese)
    }
}
