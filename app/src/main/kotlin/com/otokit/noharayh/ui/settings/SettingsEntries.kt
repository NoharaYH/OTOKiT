package com.otokit.noharayh.ui.settings

import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.settings.SettingsPalette
import com.otokit.noharayh.ui.shell.navigation.SettingsSection

internal data class SettingsEntry(val section: SettingsSection, val label: Int, val icon: Int, val color: Int)

internal val settingsEntries = listOf(
    SettingsEntry(SettingsSection.ScoreSync, R.string.settings_tile_sync, R.drawable.ic_sync_24, SettingsPalette.SYNC),
    SettingsEntry(SettingsSection.App, R.string.settings_tile_app, R.drawable.ic_settings_24, SettingsPalette.APP),
    SettingsEntry(SettingsSection.Personalization, R.string.settings_tile_personalize, R.drawable.ic_palette_24, SettingsPalette.PERSONALIZATION),
    SettingsEntry(SettingsSection.About, R.string.settings_tile_about, R.drawable.ic_info_outline_24, SettingsPalette.GRAY),
)
