package com.otokit.noharayh.ui.theme

/**
 * 设置页可选择的主题项。
 *
 * HTML 主题仍由 Registry 登记；通用占位不属于任何音游，也不伪装成 RegisteredTheme。
 */
sealed interface ThemeOption {
    val id: String
    val title: String

    data class Html(val theme: RegisteredTheme) : ThemeOption {
        override val id: String get() = theme.id
        override val title: String get() = theme.displayName
    }

    data class Placeholder(
        override val id: String,
        override val title: String,
    ) : ThemeOption
}

object ThemeOptions {

    const val PLACEHOLDER_ID: String = "placeholder"

    val placeholder: ThemeOption = ThemeOption.Placeholder(PLACEHOLDER_ID, "通用主题")

    /** 独立模式：通用占位在前，随后是该音游登记主题。 */
    fun forGame(gameLine: GameLine): List<ThemeOption> =
        listOf(placeholder) + ThemeRegistry.themesFor(gameLine).map(ThemeOption::Html)

    /** 合并模式：通用占位 → 舞萌 → 中二。 */
    fun merged(): List<ThemeOption> =
        listOf(placeholder) +
            ThemeRegistry.themesFor(GameLine.Mai).map(ThemeOption::Html) +
            ThemeRegistry.themesFor(GameLine.Chu).map(ThemeOption::Html)

    fun find(id: String?): ThemeOption? {
        if (id == null) return null
        if (id == PLACEHOLDER_ID) return placeholder
        return ThemeRegistry.findById(id)?.let(ThemeOption::Html)
    }

    fun resolveForGame(gameLine: GameLine, id: String?): ThemeOption =
        find(id)?.takeIf { option ->
            when (option) {
                is ThemeOption.Placeholder -> true
                is ThemeOption.Html -> option.theme.gameLine == gameLine
            }
        } ?: ThemeOption.Html(ThemeRegistry.defaultFor(gameLine))

    fun resolveMerged(id: String?): ThemeOption = find(id) ?: placeholder
}
