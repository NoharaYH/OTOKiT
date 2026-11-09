package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.theme.GameLine

/**
 * 由已提交的导航与选择状态生成标题展示数据。
 *
 * 设置打开时覆盖功能标题并使用固定深色文字与返回语义；功能态菜单在没有消费者前保持禁用。
 */
object TitleBarStateFactory {

    fun create(
        destination: Destination,
        currentGame: GameLine,
        settingsOpen: Boolean,
        menuEnabled: Boolean,
        functionalTextColor: Int,
        themeCircleColor: Int,
        maiDecorColor: Int,
        chuDecorColor: Int,
    ): TitleBarState = if (settingsOpen) {
        TitleBarState(
            text = TitleTexts.Setting,
            textColor = SETTINGS_TITLE_COLOR,
            circleColor = themeCircleColor,
            maiDecorColor = maiDecorColor,
            chuDecorColor = chuDecorColor,
            primaryAction = TitleBarPrimaryAction.Back,
            primaryEnabled = true,
        )
    } else {
        TitleBarState(
            text = TitleTexts.forDestination(destination, currentGame),
            textColor = functionalTextColor,
            circleColor = themeCircleColor,
            maiDecorColor = maiDecorColor,
            chuDecorColor = chuDecorColor,
            primaryAction = TitleBarPrimaryAction.Menu,
            // 只在当前窗口形态真的提供抽屉时解禁；07 之前这里是固定 false。
            primaryEnabled = menuEnabled,
        )
    }
}
