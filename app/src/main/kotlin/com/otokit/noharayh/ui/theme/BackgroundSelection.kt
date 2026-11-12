package com.otokit.noharayh.ui.theme

/**
 * 背景宿主的纯输入：HTML 主题、通用占位或纯灰无背景。
 *
 * 不包含 WebView、窗口或选择状态；由 shell 根据 ThemeOption 归一化后交给背景层。
 */
sealed interface BackgroundSelection {
    data class Html(val theme: RegisteredTheme) : BackgroundSelection
    data object Placeholder : BackgroundSelection
    data object None : BackgroundSelection
}

object BackgroundPalette {
    /** 50% 黑色；占位背景只铺这一层，不创建 HTML。 */
    const val PLACEHOLDER_COLOR: Int = 0x80000000.toInt()

    /** 无主题时的纯灰。 */
    const val NONE_COLOR: Int = 0xFF808080.toInt()

    fun colorsFor(selection: BackgroundSelection): ThemeColors = when (selection) {
        is BackgroundSelection.Html -> selection.theme.definition.colors
        BackgroundSelection.Placeholder -> ThemeColors(
            basicColor = 0xFF9E9E9E.toInt(),
            buttonColor = 0xFF757575.toInt(),
        )
        BackgroundSelection.None -> ThemeColors(
            basicColor = NONE_COLOR,
            buttonColor = 0xFF616161.toInt(),
        )
    }

    fun fromOption(option: ThemeOption): BackgroundSelection = when (option) {
        is ThemeOption.Html -> BackgroundSelection.Html(option.theme)
        is ThemeOption.Placeholder -> BackgroundSelection.Placeholder
    }
}
