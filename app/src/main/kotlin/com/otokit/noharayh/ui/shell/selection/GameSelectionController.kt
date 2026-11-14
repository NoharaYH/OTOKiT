package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeRegistry
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.NoneThemePalette
import com.otokit.noharayh.ui.theme.NoneThemeColorRole

/**
 * shell 私有的唯一选择写入口。
 *
 * 只提交合法状态、同值去重；不读写磁盘、不持有 View 或 WebView。
 * 状态变化返回 true，调用方据此决定是否重新渲染与保存。
 */
class GameSelectionController(
    initialState: GameSelectionState,
    private val lookup: (String?) -> RegisteredTheme? = ThemeRegistry::findById,
) {

    var state: GameSelectionState = initialState
        private set

    /** 选择音游；同值请求不产生新的稳定变化。 */
    fun selectGame(gameLine: GameLine): Boolean {
        if (state.currentGame == gameLine) return false
        state = state.copy(currentGame = gameLine)
        return true
    }

    /**
     * 选择某音游的主题；以登记查询校验 ID 与归属，不把可构造的 [theme] 当作验证凭证。
     *
     * 改变非当前音游的主题不改变 currentGame。
     */
    fun selectTheme(theme: RegisteredTheme): Boolean =
        selectOption(ThemeOption.Html(theme), theme.gameLine)

    fun selectThemeMode(mode: ThemeSelectionMode): Boolean {
        if (state.themeMode == mode) return false
        state = state.copy(themeMode = mode)
        return true
    }

    fun selectNoneThemeBackground(enabled: Boolean): Boolean {
        if (state.themeMode != ThemeSelectionMode.None || state.noneThemeTintBackground == enabled) return false
        state = state.copy(noneThemeTintBackground = enabled)
        return true
    }

    fun selectNoneThemeWallpaper(enabled: Boolean): Boolean {
        if (state.themeMode != ThemeSelectionMode.None || state.noneThemeUseWallpaper == enabled) return false
        val colors = NoneThemePalette.fromSeed(state.noneThemeSeedColor, state.noneThemeWallpaperSecondary).colors
        state = state.copy(noneThemeUseWallpaper = enabled,
            noneThemePrimaryColor = state.noneThemePrimaryColor ?: colors.primaryColor,
            noneThemeSecondaryColor = state.noneThemeSecondaryColor ?: colors.secondaryColor)
        return true
    }

    fun selectNoneThemeColor(role: NoneThemeColorRole, color: Int): Boolean {
        if (state.themeMode != ThemeSelectionMode.None || state.noneThemeUseWallpaper || color ushr 24 != 255) return false
        val next = when (role) {
            NoneThemeColorRole.Primary -> state.copy(noneThemePrimaryColor = color)
            NoneThemeColorRole.Secondary -> state.copy(noneThemeSecondaryColor = color)
        }
        if (next == state) return false
        state = next
        return true
    }

    fun updateWallpaperSeed(color: Int, secondary: Int? = null): Boolean {
        if (color ushr 24 != 255 || (secondary != null && secondary ushr 24 != 255)) return false
        if (state.noneThemeSeedColor == color && state.noneThemeWallpaperSecondary == secondary) return false
        state = state.copy(noneThemeSeedColor = color, noneThemeWallpaperSecondary = secondary)
        return true
    }

    /**
     * 统一提交主题选项。
     *
     * 独立模式写对应音游字段；合并模式只写 mergedThemeId；无模式拒绝修改。
     * 通用占位不属于任何音游，独立模式必须由 target 指定应用对象。
     */
    fun selectOption(option: ThemeOption, target: GameLine? = null): Boolean {
        if (state.themeMode == ThemeSelectionMode.None) return false
        // 调用方传来的对象不是登记凭证；所有模式都必须先验证稳定 ID。
        when (option) {
            is ThemeOption.Html -> {
                val registered = lookup(option.id) ?: return false
                if (registered.gameLine != option.theme.gameLine) return false
            }
            is ThemeOption.Placeholder -> if (option.id != ThemeOptions.PLACEHOLDER_ID) return false
        }

        if (state.themeMode == ThemeSelectionMode.Merged) {
            if (state.mergedThemeId == option.id) return false
            state = state.copy(mergedThemeId = option.id)
            return true
        }

        val gameLine = target ?: when (option) {
            is ThemeOption.Html -> option.theme.gameLine
            is ThemeOption.Placeholder -> return false
        }
        val registered = when (option) {
            is ThemeOption.Html -> lookup(option.id)?.takeIf {
                it.id == option.id && it.gameLine == gameLine
            }
            is ThemeOption.Placeholder -> null
        }
        if (option !is ThemeOption.Placeholder && registered == null) return false

        val changed = when (gameLine) {
            GameLine.Mai -> state.maiThemeId != option.id
            GameLine.Chu -> state.chuThemeId != option.id
        }
        if (!changed) return false

        state = when (gameLine) {
            GameLine.Mai -> state.copy(maiThemeId = option.id)
            GameLine.Chu -> state.copy(chuThemeId = option.id)
        }
        return true
    }
}
