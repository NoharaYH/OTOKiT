package com.otokit.noharayh.ui.animation.common

import android.view.View
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions

/** 上下边的外圆角比例，执行器不依赖分组容器或业务组件。 */
internal class GroupShape(private val host: View, private val onCorners: (Float, Float) -> Unit) {
    private var target = 1f to 1f
    private val transition = ContentTransition(target, { a, b, t ->
        (a.first + (b.first - a.first) * t) to (a.second + (b.second - a.second) * t)
    }, ::render)

    fun setEdges(first: Float, last: Float, animate: Boolean = true) {
        val next = first.coerceIn(0f, 1f) to last.coerceIn(0f, 1f)
        if (next == target && animate) return
        target = next
        transition.play(next, animate && host.isAttachedToWindow && host.isLaidOut)
    }
    fun finish() = transition.finish()
    private fun render(edges: Pair<Float, Float>) {
        val density = host.resources.displayMetrics.density
        val inner = SettingsGroupDimensions.INNER_RADIUS_DP * density
        val delta = (SettingsGroupDimensions.OUTER_RADIUS_DP - SettingsGroupDimensions.INNER_RADIUS_DP) * density
        onCorners(inner + delta * edges.first, inner + delta * edges.second)
    }
}
