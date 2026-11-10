package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass

/**
 * 窗口形态到外围占用的纯映射。
 *
 * Compact：只占标题行顶部；Medium/Expanded：只占左侧 Rail 宽度。
 * 阈值来自外部 window 分类，这里不重新定义 600/840。
 */
object ChromeLayoutPolicy {

    fun insetsFor(
        layoutClass: WindowLayoutClass,
        titleHeightPx: Int,
        railWidthPx: Int,
    ): ChromeInsets = when (layoutClass) {
        WindowLayoutClass.Compact -> ChromeInsets(top = titleHeightPx.coerceAtLeast(0))
        WindowLayoutClass.Medium, WindowLayoutClass.Expanded ->
            ChromeInsets(left = railWidthPx.coerceAtLeast(0))
    }

    fun usesRail(layoutClass: WindowLayoutClass): Boolean =
        layoutClass != WindowLayoutClass.Compact
}
