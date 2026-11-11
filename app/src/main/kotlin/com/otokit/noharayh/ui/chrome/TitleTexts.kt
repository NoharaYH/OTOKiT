package com.otokit.noharayh.ui.chrome

import androidx.annotation.StringRes
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.settings.SettingsPalette
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.theme.GameLine

/** 标题的一对文案：上英文、下中文。 */
data class TitleText(@StringRes val english: Int, @StringRes val chinese: Int)

/**
 * 功能标题映射，文案来自旧 `NavText`，不由主题展示名推导。
 *
 * 设置标题覆盖功能标题，但不改变 destination 或 currentGame。
 */
object TitleTexts {

    val Setting: TitleText = TitleText(R.string.title_setting, R.string.title_zh_setting)

    fun forSettings(section: SettingsSection?): TitleText = when (section) {
        null -> Setting
        SettingsSection.ScoreSync -> TitleText(R.string.title_settings_sync, R.string.settings_tile_sync)
        SettingsSection.App -> TitleText(R.string.title_settings_app, R.string.settings_tile_app)
        SettingsSection.Personalization -> TitleText(R.string.title_settings_personalization, R.string.settings_tile_personalize)
        SettingsSection.About -> TitleText(R.string.title_settings_about, R.string.settings_tile_about)
    }

    fun colorForSettings(section: SettingsSection?): Int = when (section) {
        null, SettingsSection.About -> SettingsPalette.GRAY
        SettingsSection.ScoreSync -> SettingsPalette.SYNC
        SettingsSection.App -> SettingsPalette.APP
        SettingsSection.Personalization -> SettingsPalette.PERSONALIZATION
    }

    fun forDestination(destination: Destination, gameLine: GameLine): TitleText = when (destination) {
        Destination.ScoreSync -> when (gameLine) {
            GameLine.Mai -> TitleText(R.string.title_mai_score_sync, R.string.title_zh_mai_score_sync)
            GameLine.Chu -> TitleText(R.string.title_chu_score_sync, R.string.title_zh_chu_score_sync)
        }

        Destination.MusicData -> when (gameLine) {
            GameLine.Mai -> TitleText(R.string.title_mai_music_data, R.string.title_zh_mai_music_data)
            GameLine.Chu -> TitleText(R.string.title_chu_music_data, R.string.title_zh_chu_music_data)
        }

        Destination.ComingSoon -> TitleText(R.string.title_coming_soon, R.string.title_zh_coming_soon)
    }
}
