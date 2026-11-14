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

    /** 无主题时的浅灰，与纯白前景容器保持明暗层次。 */
    const val NONE_COLOR: Int = 0xFFE6E6E6.toInt()

    fun colorsFor(selection: BackgroundSelection, materkitColors: ThemeColors): ThemeColors = when (selection) {
        is BackgroundSelection.Html -> selection.theme.definition.colors
        BackgroundSelection.Placeholder -> materkitColors
        BackgroundSelection.None -> NoneThemePalette.default.colors
    }

    fun fromOption(option: ThemeOption): BackgroundSelection = when (option) {
        is ThemeOption.Html -> BackgroundSelection.Html(option.theme)
        is ThemeOption.Placeholder -> BackgroundSelection.Placeholder
    }
}
