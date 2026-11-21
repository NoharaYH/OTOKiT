package com.otokit.noharayh.ui.kit.common

import android.view.animation.AnimationUtils
import android.view.animation.LinearInterpolator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ScrollView
import androidx.core.graphics.withClip
import com.otokit.noharayh.ui.animation.common.pageSettleInterpolator
import com.otokit.noharayh.ui.animation.common.resistedDragDistance
import com.otokit.noharayh.ui.constants.motion.NavigationTransition
import com.otokit.noharayh.ui.constants.motion.ScrollEdgeMotion
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/** 用真实滚动范围计算边界，超出范围的触摸只移动视口里的内容。 */
internal open class EdgePullScrollView(
    context: Context,
    private val overshootPx: Float,
    private val pullWhenContentFits: Boolean = false,
    private val horizontalDrawingOutsetPx: Int = 0,
) : ScrollView(context) {
    private class FittedPull(val pointer: Int, val x: Float, val y: Float, val initialPull: Float) {
        var captured = false
    }

    private val edgePullEnabled = overshootPx > 0f
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var fittedPull: FittedPull? = null
    var isTouchActive = false
        private set
    private var edgePull = 0f
    private var flingSpeed = 0f
    private var lastFlingFrameMs = 0L
    private var edgeReturn: ValueAnimator? = null

    init { if (edgePullEnabled) overScrollMode = OVER_SCROLL_NEVER }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!edgePullEnabled) return super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            flingSpeed = 0f
            catchEdgeReturn()
            isTouchActive = true
            fittedPull = if (pullWhenContentFits && childCount > 0 && !canScrollVertically(-1) && !canScrollVertically(1)) {
                FittedPull(event.getPointerId(0), event.x, event.y, edgePull)
            } else null
        }
        val handled = super.dispatchTouchEvent(event)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL ||
            (action == MotionEvent.ACTION_DOWN && !handled)) {
            isTouchActive = false
            fittedPull = null
            if (edgePull != 0f) startEdgeReturn()
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        followFittedPull(event) || super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        followFittedPull(event) || super.onTouchEvent(event)

    override fun onNestedScrollAccepted(child: View, target: View, axes: Int) {
        super.onNestedScrollAccepted(child, target, axes)
        if (axes and SCROLL_AXIS_VERTICAL != 0 && (target.canScrollVertically(-1) || target.canScrollVertically(1))) {
            fittedPull = null
        }
    }

    private fun followFittedPull(event: MotionEvent): Boolean {
        // ScrollView does not intercept a drag when the content has no scroll range.
        val pull = fittedPull ?: return false
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return false
        val index = event.findPointerIndex(pull.pointer)
        if (index < 0) return pull.captured
        val dy = event.getY(index) - pull.y
        if (!pull.captured) {
            val dx = abs(event.getX(index) - pull.x)
            if (maxOf(dx, abs(dy)) <= touchSlop) return false
            if (dx >= abs(dy)) {
                fittedPull = null
                return false
            }
            pull.captured = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        renderEdgePull(pull.initialPull - (abs(dy) - touchSlop).coerceAtLeast(0f) * dy.sign)
        return true
    }

    override fun overScrollBy(deltaX: Int, deltaY: Int, scrollX: Int, scrollY: Int,
        scrollRangeX: Int, scrollRangeY: Int, maxOverScrollX: Int, maxOverScrollY: Int, isTouchEvent: Boolean): Boolean {
        var scrollDelta = deltaY
        if (edgePullEnabled && isTouchEvent && scrollRangeY > 0) {
            // 反向先收回拉出的位移，再恢复真实滚动。
            val requested = scrollY + deltaY + edgePull
            val clamped = requested.coerceIn(0f, scrollRangeY.toFloat())
            scrollDelta = clamped.roundToInt() - scrollY
            renderEdgePull(requested - clamped)
        } else if (edgePullEnabled && !isTouchEvent && flingSpeed != 0f && deltaY != 0) {
            // ScrollView supplies the native fling displacement before clamping to the scroll range.
            val now = AnimationUtils.currentAnimationTimeMillis()
            val elapsed = now - lastFlingFrameMs
            val speed = if (elapsed > 0) deltaY * 1000f / elapsed else flingSpeed
            lastFlingFrameMs = now
            val requested = scrollY + deltaY
            val excess = requested - requested.coerceIn(0, scrollRangeY)
            if (excess != 0 && excess * flingSpeed > 0f) {
                flingSpeed = 0f
                if (ValueAnimator.areAnimatorsEnabled()) {
                    renderEdgePull(excess.toFloat())
                    startEdgeReturn(speed)
                }
            }
        }
        return super.overScrollBy(deltaX, scrollDelta, scrollX, scrollY,
            scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent)
    }

    override fun dispatchNestedScroll(dxConsumed: Int, dyConsumed: Int, dxUnconsumed: Int, dyUnconsumed: Int,
        offsetInWindow: IntArray?): Boolean {
        // 边界的剩余位移已用于拉动正文，不能再带动外层容器滚动。
        val handlesPull = isTouchActive && dyUnconsumed != 0 && (canScrollVertically(-1) || canScrollVertically(1))
        return super.dispatchNestedScroll(dxConsumed, dyConsumed, dxUnconsumed,
            if (handlesPull) 0 else dyUnconsumed, offsetInWindow)
    }

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean {
        if (!isTouchActive || edgePull == 0f) return super.dispatchNestedFling(velocityX, velocityY, consumed)
        return true
    }

    override fun fling(velocityY: Int) {
        if (isTouchActive && edgePull != 0f && velocityY != 0) return
        flingSpeed = velocityY.toFloat()
        lastFlingFrameMs = AnimationUtils.currentAnimationTimeMillis()
        super.fling(velocityY)
    }

    override fun dispatchDraw(canvas: Canvas) {
        if (edgePullEnabled || horizontalDrawingOutsetPx > 0) {
            canvas.withClip(-horizontalDrawingOutsetPx, scrollY, width + horizontalDrawingOutsetPx, scrollY + height) {
                super.dispatchDraw(this)
            }
        } else super.dispatchDraw(canvas)
    }

    fun resetEdgePull() {
        if (!edgePullEnabled) return
        edgeReturn?.cancel()
        fittedPull = null
        flingSpeed = 0f
        renderEdgePull(0f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        resetEdgePull()
    }

    override fun onDetachedFromWindow() {
        isTouchActive = false
        resetEdgePull()
        super.onDetachedFromWindow()
    }

    private fun renderEdgePull(distance: Float) {
        edgePull = distance
        getChildAt(0)?.translationY = -distance.sign * resistedDragDistance(abs(distance) * ScrollEdgeMotion.PULL_GAIN, 0f, overshootPx)
    }

    private fun catchEdgeReturn() {
        if (edgeReturn?.isRunning != true) return
        edgeReturn?.cancel()
        val content = getChildAt(0) ?: return
        val offset = -content.translationY
        if (offset != 0f && canScrollVertically(offset.sign.toInt())) {
            // The bounce has crossed back inside the viewport. Pick it up as ordinary scroll.
            val before = scrollY
            scrollBy(0, offset.roundToInt())
            content.translationY += scrollY - before
        }
        // Inverse of the finger's rational resistance, evaluated only when catching a moving page.
        val visible = -content.translationY
        val remaining = (overshootPx - abs(visible)).coerceAtLeast(.01f * overshootPx)
        edgePull = visible * overshootPx / (ScrollEdgeMotion.PULL_GAIN * remaining)
    }

    private fun startEdgeReturn(impactVelocity: Float = 0f) {
        val offset = -(getChildAt(0)?.translationY ?: return)
        edgePull = 0f
        if (!ValueAnimator.areAnimatorsEnabled()) {
            renderEdgePull(0f)
            return
        }
        val compression = (abs(offset) / overshootPx).coerceIn(0f, 1f)
        val remaining = 1f - compression
        val limit = overshootPx * ScrollEdgeMotion.MAX_IMPACT_SPEED_PER_EXTENT * remaining
        val velocity = (impactVelocity * ScrollEdgeMotion.PULL_GAIN * remaining * remaining).coerceIn(-limit, limit)
        val curve = pageSettleInterpolator()
        edgeReturn?.cancel()
        edgeReturn = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NavigationTransition.DURATION_MS
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedFraction
                val rest = 1f - curve.getInterpolation(t)
                // Carry the incoming fling through contact; only the selected curve supplies rebound.
                val travel = abs(velocity) * (duration / 1000f) * t
                val carry = velocity.sign * resistedDragDistance(travel, 0f, overshootPx * remaining) *
                    rest.coerceAtLeast(0f)
                getChildAt(0)?.translationY = -(offset * rest + carry)
            }
            start()
        }
    }
}
