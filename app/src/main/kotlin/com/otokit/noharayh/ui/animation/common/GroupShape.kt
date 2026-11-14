package com.otokit.noharayh.ui.animation.common

import android.view.View
import com.otokit.noharayh.ui.constants.motion.TransitionTiming

/** 插值上下边圆角；半径为 px，尺寸与时序由消费组件注入。 */
internal class GroupShape(private val host: View, private val innerRadius: Float, private val outerRadius: Float,
    timing: TransitionTiming, private val onCorners: (Float, Float) -> Unit,
) {
    private var target = 1f to 1f
    private val transition = ContentTransition(target, timing, { a, b, t ->
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
        val delta = outerRadius - innerRadius
        onCorners(innerRadius + delta * edges.first, innerRadius + delta * edges.second)
    }
}
