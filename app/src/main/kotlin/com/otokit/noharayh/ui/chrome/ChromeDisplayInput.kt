package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.theme.GameLine

/** 外围 UI 的只读展示输入；不包含控制器、偏好或页面对象。 */
data class ChromeDisplayInput(
    val destination: Destination,
    val currentGame: GameLine,
    val settingsOpen: Boolean,
    val themeColor: Int,
    val maiTextColor: Int,
    val chuTextColor: Int,
    val maiDecorColor: Int,
    val chuDecorColor: Int,
    val gameFrame: GameFrame?,
    val navDirection: Int,
)
