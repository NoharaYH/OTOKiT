package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.theme.GameLine

/**
 * 由已提交的导航、选择与背景帧生成标题展示数据。
 *
 * 功能态始终提供 Mai/Chu 两套标题；设置态再覆盖固定标题与返回语义。
 */
object TitleBarStateFactory {

    fun create(
        destination: Destination,
        currentGame: GameLine,
        settingsOpen: Boolean,
        menuEnabled: Boolean,
        gameFrame: GameFrame?,
        navDirection: Int,
        maiTextColor: Int,
        chuTextColor: Int,
        themeCircleColor: Int,
        maiDecorColor: Int,
        chuDecorColor: Int,
    ): TitleBarState = TitleBarState(
        maiTitle = TitleTexts.forDestination(destination, GameLine.Mai),
        chuTitle = TitleTexts.forDestination(destination, GameLine.Chu),
        settingTitle = TitleTexts.Setting,
        currentGame = currentGame,
        gameFrame = gameFrame,
        navDirection = navDirection,
        settingsOpen = settingsOpen,
        maiTextColor = maiTextColor,
        chuTextColor = chuTextColor,
        settingTextColor = SETTINGS_TITLE_COLOR,
        circleColor = themeCircleColor,
        maiDecorColor = maiDecorColor,
        chuDecorColor = chuDecorColor,
        primaryAction = if (settingsOpen) TitleBarPrimaryAction.Back else TitleBarPrimaryAction.Menu,
        primaryEnabled = if (settingsOpen) true else menuEnabled,
    )
}
