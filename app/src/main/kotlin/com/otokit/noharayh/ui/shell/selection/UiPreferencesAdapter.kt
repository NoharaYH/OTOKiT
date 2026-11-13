package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.storage.preferences.UiPreferenceRecord
import com.otokit.noharayh.storage.preferences.UiPreferences
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions

/**
 * 把 storage 的原始偏好字段映射成选择恢复候选，并把稳定状态编码后写回。
 *
 * 由 shell 控制调用时机；这里不保存第二份“当前选择”，也不决定恢复优先级。
 */
class UiPreferencesAdapter(private val preferences: UiPreferences) {

    fun read(): SelectionSource = preferences.readSelection().toSelectionSource()

    fun readBackgroundRendering(): BackgroundRenderingOptions = preferences.readSelection().let {
        BackgroundRenderingOptions(it.backgroundHardwareAcceleration ?: true, it.backgroundHtmlAnimations ?: true)
    }

    fun writeBackgroundRendering(options: BackgroundRenderingOptions) {
        preferences.writeBackgroundRendering(options.hardwareAccelerated, options.htmlAnimations)
    }

    fun write(state: GameSelectionState) {
        preferences.writeSelection(
            version = GameSelectionFormat.VERSION,
            currentGame = GameSelectionFormat.gameValue(state.currentGame),
            maiThemeId = state.maiThemeId,
            chuThemeId = state.chuThemeId,
            themeMode = GameSelectionFormat.modeValue(state.themeMode),
            mergedThemeId = state.mergedThemeId,
            noneThemeSeedColor = state.noneThemeSeedColor,
            noneThemeTintBackground = state.noneThemeTintBackground,
            noneThemeUseWallpaper = state.noneThemeUseWallpaper,
            noneThemePrimaryColor = state.noneThemePrimaryColor,
            noneThemeSecondaryColor = state.noneThemeSecondaryColor,
        )
    }
}

private fun UiPreferenceRecord.toSelectionSource(): SelectionSource = SelectionSource(
    version = version,
    currentGame = currentGame,
    maiThemeId = maiThemeId,
    chuThemeId = chuThemeId,
    themeMode = themeMode,
    mergedThemeId = mergedThemeId,
    noneThemeSeedColor = noneThemeSeedColor,
    noneThemeTintBackground = noneThemeTintBackground,
    noneThemeUseWallpaper = noneThemeUseWallpaper,
    noneThemePrimaryColor = noneThemePrimaryColor,
    noneThemeSecondaryColor = noneThemeSecondaryColor,
)
