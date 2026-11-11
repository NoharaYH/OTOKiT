package com.otokit.noharayh.ui.shell.navigation

/**
 * 导航语义顺序。
 *
 * 只用于切页方向等导航判断，不包含文案、图标或颜色；
 * chrome 的展示表可以自行排列，但不能反过来成为导航顺序的事实来源。
 */
object NavigationOrder {

    val destinations: List<Destination> = Destination.entries.toList()

    fun indexOf(destination: Destination): Int = destinations.indexOf(destination)

    fun direction(from: Destination?, to: Destination): Int {
        if (from == to) return 0
        val oldIndex = from?.let(::indexOf) ?: return 1
        val newIndex = indexOf(to)
        return when {
            oldIndex < 0 || newIndex < 0 -> 1
            newIndex > oldIndex -> 1
            newIndex < oldIndex -> -1
            else -> 0
        }
    }
}
