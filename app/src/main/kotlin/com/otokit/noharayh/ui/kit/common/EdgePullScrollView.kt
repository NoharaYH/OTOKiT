package com.otokit.noharayh.ui.kit.common

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.ScrollView
import androidx.core.graphics.withClip
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.animation.common.resistedDragDistance
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
    private val edgeReturn = if (edgePullEnabled) SpringAnimation(FloatValueHolder()).apply {
        spring = SpringForce(0f).setDampingRatio(ScrollEdgeMotion.RETURN_DAMPING)
            .setStiffness(ScrollEdgeMotion.RETURN_STIFFNESS)
        addUpdateListener { _, value, _ -> renderEdgePull(value) }
    } else null

    init { if (edgePullEnabled) overScrollMode = OVER_SCROLL_NEVER }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!edgePullEnabled) return super.dispatchTouchEvent(event)
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            edgeReturn?.cancel()
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
            releaseEdgePull()
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean =
        followFittedPull(event) || super.onInterceptTouchEvent(event)

    override fun onTouchEvent(event: MotionEvent): Boolean =
        followFittedPull(event) || super.onTouchEvent(event)

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

    override fun dispatchNestedFling(velocityX: Float, velocityY: Float, consumed: Boolean): Boolean =
        (isTouchActive && edgePull != 0f) || super.dispatchNestedFling(velocityX, velocityY, consumed)

    override fun fling(velocityY: Int) {
        if (!isTouchActive || edgePull == 0f || velocityY == 0) super.fling(velocityY)
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
        getChildAt(0)?.translationY = -distance.sign * resistedDragDistance(abs(distance), 0f, overshootPx)
    }

    private fun releaseEdgePull() {
        if (edgePull == 0f) return
        if (ValueAnimator.areAnimatorsEnabled()) {
            edgeReturn?.setStartValue(edgePull)?.setStartVelocity(0f)?.animateToFinalPosition(0f)
        } else renderEdgePull(0f)
    }
}
