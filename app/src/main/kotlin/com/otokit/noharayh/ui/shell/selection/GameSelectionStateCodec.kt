package com.otokit.noharayh.ui.shell.selection

import android.os.Bundle

/**
 * 选择快照的 Bundle 读写。
 *
 * 使用独立容器键，不覆盖导航的 `ui_shell_state`；只返回原始字段，
 * 不在读取阶段默认化或调用 Registry。
 */
object GameSelectionStateCodec {

    const val BUNDLE_KEY: String = "ui_shell_selection"

    private const val KEY_VERSION = "version"
    private const val KEY_CURRENT_GAME = "current_game"
    private const val KEY_THEME_MAI = "theme_mai"
    private const val KEY_THEME_CHU = "theme_chu"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_MERGED_THEME = "merged_theme"
    private const val KEY_NONE_SEED = "none_theme_seed_color"
    private const val KEY_NONE_BACKGROUND = "none_theme_tint_background"

    /**
     * 无类型读取用于区分“缺失”和“类型错误”：带类型的 getter 在类型不符时会抛异常或返回默认值。
     * 该 API 自 33 起标记弃用，但 26—35 均可用。
     */
    @Suppress("DEPRECATION")
    fun read(bundle: Bundle?): SelectionSource {
        val container = bundle?.getBundle(BUNDLE_KEY)
        return SelectionSource(
            version = container?.get(KEY_VERSION) as? Int,
            currentGame = container?.get(KEY_CURRENT_GAME) as? String,
            maiThemeId = container?.get(KEY_THEME_MAI) as? String,
            chuThemeId = container?.get(KEY_THEME_CHU) as? String,
            themeMode = container?.get(KEY_THEME_MODE) as? String,
            mergedThemeId = container?.get(KEY_MERGED_THEME) as? String,
            noneThemeSeedColor = container?.get(KEY_NONE_SEED) as? Int,
            noneThemeTintBackground = container?.get(KEY_NONE_BACKGROUND) as? Boolean,
        )
    }

    fun write(bundle: Bundle, state: GameSelectionState) {
        val container = Bundle().apply {
            putInt(KEY_VERSION, GameSelectionFormat.VERSION)
            putString(KEY_CURRENT_GAME, GameSelectionFormat.gameValue(state.currentGame))
            putString(KEY_THEME_MAI, state.maiThemeId)
            putString(KEY_THEME_CHU, state.chuThemeId)
            putString(KEY_THEME_MODE, GameSelectionFormat.modeValue(state.themeMode))
            putString(KEY_MERGED_THEME, state.mergedThemeId)
            putInt(KEY_NONE_SEED, state.noneThemeSeedColor)
            putBoolean(KEY_NONE_BACKGROUND, state.noneThemeTintBackground)
        }
        bundle.putBundle(BUNDLE_KEY, container)
    }
}
