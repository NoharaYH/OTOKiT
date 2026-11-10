package com.otokit.noharayh.ui.chrome

/** 外围 UI 的只读展示输入；不包含控制器、偏好或页面对象。 */
data class ChromeDisplayInput(
    val destination: com.otokit.noharayh.ui.shell.navigation.Destination,
    val currentGame: com.otokit.noharayh.ui.theme.GameLine,
    val settingsOpen: Boolean,
    val themeColor: Int,
    val maiDecorColor: Int,
    val chuDecorColor: Int,
)
