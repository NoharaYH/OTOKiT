package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme

/**
 * shell 私有的唯一选择写入口。
 *
 * 只提交合法状态、同值去重；不读写磁盘、不持有 View 或 WebView。
 * 状态变化返回 true，调用方据此决定是否重新渲染与保存。
 */
class GameSelectionController(initialState: GameSelectionState) {

    var state: GameSelectionState = initialState
        private set

    /** 选择音游；同值请求不产生新的稳定变化。 */
    fun selectGame(gameLine: GameLine): Boolean {
        if (state.currentGame == gameLine) return false
        state = state.copy(currentGame = gameLine)
        return true
    }

    /**
     * 选择某音游的主题；[theme] 必须来自 Registry 的登记项，归属由类型本身保证。
     *
     * 改变非当前音游的主题不改变 currentGame。
     */
    fun selectTheme(theme: RegisteredTheme): Boolean {
        val changed = when (theme.gameLine) {
            GameLine.Mai -> state.maiThemeId != theme.id
            GameLine.Chu -> state.chuThemeId != theme.id
        }
        if (!changed) return false

        state = when (theme.gameLine) {
            GameLine.Mai -> state.copy(maiThemeId = theme.id)
            GameLine.Chu -> state.copy(chuThemeId = theme.id)
        }
        return true
    }
}
