package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.theme.ThemeOption

/** 在过滤后的实际菜单上确定分组首尾；新增登记项无需维护任何 ID 或下标。 */
internal data class ThemeMenuEdge(val top: Boolean, val bottom: Boolean)

internal fun themeMenuEdges(options: List<ThemeOption>): List<ThemeMenuEdge> {
    fun group(option: ThemeOption): Any = when (option) {
        is ThemeOption.Html -> option.theme.gameLine
        is ThemeOption.Placeholder -> option.id
    }
    return options.mapIndexed { index, option ->
        val key = group(option)
        ThemeMenuEdge(
            top = index == 0 || group(options[index - 1]) != key,
            bottom = index == options.lastIndex || group(options[index + 1]) != key,
        )
    }
}
