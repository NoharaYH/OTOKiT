package com.otokit.noharayh.ui.kit.settings

/** 面板静止外轮廓的 px 半径；末项圆角仍由菜单原有的折叠几何提供。 */
internal data class ThemePanelCorners(val top: Float, val bottom: Float, val maximumBottom: Float) {
    companion object {
        fun resolve(expansion: Float, collapsed: Float, expandedTop: Float,
            menuBottom: Float, menuMaximum: Float, padding: Float,
        ): ThemePanelCorners {
            val t = expansion.coerceIn(0f, 1f)
            fun mix(target: Float) = collapsed + (target - collapsed) * t
            return ThemePanelCorners(mix(expandedTop), mix(menuBottom + padding), mix(menuMaximum + padding))
        }
    }
}
