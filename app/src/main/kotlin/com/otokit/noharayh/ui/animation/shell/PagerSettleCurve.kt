package com.otokit.noharayh.ui.animation.shell

import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * 双页 Pager 仍负责拖动、选页和发布进度；这里只替换松手吸附的时间曲线。
 * 复用原 SnapHelper 的目标计算，公开解绑其默认收尾，避免两份滚动时钟竞争。
 */
internal class PagerSettleCurve(
    private val recycler: RecyclerView,
    private val snap: PagerSnapHelper,
    private val canSettle: () -> Boolean,
) : RecyclerView.OnFlingListener(), AutoCloseable {
    private val interpolator = MotionInterpolators.of(PagerPairTransition.CURVE)
    private var scrolled = false
    private val listener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (dx != 0 || dy != 0) scrolled = true
        }

        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE || !scrolled) return
            scrolled = false
            if (!canSettle()) return
            val layout = recycler.layoutManager ?: return
            val target = snap.findSnapView(layout) ?: return
            val distance = snap.calculateDistanceToFinalSnap(layout, target) ?: return
            if (distance[0] != 0 || distance[1] != 0) {
                // 与 SnapHelper 原来的 smoothScrollBy 相同，由 RecyclerView 根据距离算时长。
                recycler.smoothScrollBy(distance[0], distance[1], interpolator)
            }
        }
    }

    init {
        snap.attachToRecyclerView(null)
        recycler.onFlingListener = this
        recycler.addOnScrollListener(listener)
    }

    override fun onFling(velocityX: Int, velocityY: Int): Boolean {
        if (!canSettle() || max(abs(velocityX), abs(velocityY)) <= recycler.minFlingVelocity) return false
        val layout = recycler.layoutManager ?: return false
        val position = snap.findTargetSnapPosition(layout, velocityX, velocityY)
        if (position == RecyclerView.NO_POSITION) return false
        // 双页常驻，目标已布局；不改变原来的选页阈值或方向判断。
        val target = layout.findViewByPosition(position) ?: return false
        val distance = snap.calculateDistanceToFinalSnap(layout, target) ?: return false
        val pixels = max(abs(distance[0]), abs(distance[1]))
        if (pixels == 0) return true
        // PagerSnapHelper/LinearSmoothScroller 的原收尾时长：100ms/inch，滚动段最多 100ms。
        val scrollTime = min(100, ceil(pixels * (100f / recycler.resources.displayMetrics.densityDpi)).toInt())
        val duration = ceil(scrollTime / 0.3356).toInt()
        recycler.smoothScrollBy(distance[0], distance[1], interpolator, duration)
        return true
    }

    override fun close() {
        recycler.removeOnScrollListener(listener)
        if (recycler.onFlingListener === this) recycler.onFlingListener = null
        scrolled = false
    }
}
