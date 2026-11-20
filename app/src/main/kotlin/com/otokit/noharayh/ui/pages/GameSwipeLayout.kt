package com.otokit.noharayh.ui.pages

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.resistedDragDistance
import com.otokit.noharayh.ui.constants.dimensions.GameSwipeDimensions
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
import com.otokit.noharayh.ui.constants.motion.TransitionTiming
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.theme.GameLine
import kotlin.math.abs
import kotlin.math.sign

/** Owns the foreground gesture and its transient frames. The shell confirms the game choice. */
class GameSwipeLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    private class Drag(val game: GameLine, val x: Float, val y: Float) {
        val velocity: VelocityTracker = VelocityTracker.obtain()
        var captured = false
    }

    private val configuration = ViewConfiguration.get(context)
    private val edgeOvershoot = dp(context, GameSwipeDimensions.EDGE_OVERSHOOT_DP).toFloat()
    private var drag: Drag? = null
    private var settleTarget: GameLine? = null
    private var position = 0f
    private var inputEnabled = false
    private val transition = ContentTransition(0f, TransitionTiming(PagerPairTransition.SWIPE_SETTLE_MS, PagerPairTransition.CURVE),
        { from, to, progress -> from + (to - from) * progress }, ::onSettleFrame)

    internal var selectedGame: () -> GameLine = { GameLine.Mai }
    internal var onSelection: ((GameLine) -> Unit)? = null
    internal var onFrameChanged: (() -> Unit)? = null
    internal var phase = GamePhase.Idle
        private set
    internal var progress = 0f
        private set
    internal var edgePull = 0f
        private set

    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES }

    internal fun setGameSwipeEnabled(enabled: Boolean) {
        inputEnabled = enabled
        if (!enabled) cancelGameSwipe()
    }

    internal fun cancelGameSwipe() {
        clearDrag()
        settleTarget = null
        transition.stop()
        if (phase != GamePhase.Idle) publish(endpoint(selectedGame()), GamePhase.Idle)
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        if (action == MotionEvent.ACTION_DOWN) {
            clearDrag()
            if (inputEnabled && width > 0) {
                if (phase != GamePhase.Idle) return true
                drag = Drag(selectedGame(), event.x, event.y).also { it.velocity.addMovement(event) }
            }
            return super.dispatchTouchEvent(event) || drag != null
        }

        val gesture = drag ?: return phase != GamePhase.Idle || super.dispatchTouchEvent(event)
        gesture.velocity.addMovement(event)
        if (action == MotionEvent.ACTION_MOVE) {
            val dx = event.x - gesture.x
            if (!gesture.captured && maxOf(abs(dx), abs(event.y - gesture.y)) > configuration.scaledTouchSlop) {
                if (abs(dx) <= abs(event.y - gesture.y)) {
                    clearDrag()
                    return super.dispatchTouchEvent(event)
                }
                gesture.captured = true
                parent?.requestDisallowInterceptTouchEvent(true)
                val cancel = MotionEvent.obtain(event)
                cancel.action = MotionEvent.ACTION_CANCEL
                super.dispatchTouchEvent(cancel)
                cancel.recycle()
            }
            if (gesture.captured) {
                val distance = (abs(dx) - configuration.scaledTouchSlop).coerceAtLeast(0f) * dx.sign
                publish(endpoint(gesture.game) - distance, GamePhase.Dragging)
                return true
            }
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (gesture.captured) {
                settleTo(if (action == MotionEvent.ACTION_UP) releaseTarget(gesture) else gesture.game)
                return true
            }
            clearDrag()
        }
        return super.dispatchTouchEvent(event)
    }

    private fun releaseTarget(gesture: Drag): GameLine {
        if (position <= 0f) return GameLine.Mai
        if (position >= width) return GameLine.Chu
        gesture.velocity.computeCurrentVelocity(1000, configuration.scaledMaximumFlingVelocity.toFloat())
        val vx = gesture.velocity.xVelocity
        return when {
            abs(vx) > abs(gesture.velocity.yVelocity) && abs(vx) >= configuration.scaledMinimumFlingVelocity ->
                if (vx < 0f) GameLine.Chu else GameLine.Mai
            position >= width / 2f -> GameLine.Chu
            else -> GameLine.Mai
        }
    }

    private fun settleTo(target: GameLine) {
        clearDrag()
        settleTarget = target
        transition.play(endpoint(target), from = position)
    }

    private fun onSettleFrame(value: Float) {
        if (transition.isRunning) publish(value, GamePhase.Settling)
        else {
            val target = settleTarget ?: return
            settleTarget = null
            phase = GamePhase.Idle
            if (target != selectedGame()) onSelection?.invoke(target)
            publish(endpoint(selectedGame()), GamePhase.Idle)
        }
    }

    private fun publish(value: Float, phase: GamePhase) {
        position = value
        this.phase = phase
        val bounded = value.coerceIn(0f, width.toFloat())
        progress = if (width > 0) bounded / width else 0f
        val extra = bounded - value
        edgePull = extra.sign * resistedDragDistance(abs(extra), 0f, edgeOvershoot)
        onFrameChanged?.invoke()
    }

    private fun endpoint(game: GameLine): Float = if (game == GameLine.Mai) 0f else width.toFloat()

    private fun clearDrag() {
        drag?.let {
            it.velocity.recycle()
            if (it.captured) parent?.requestDisallowInterceptTouchEvent(false)
        }
        drag = null
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cancelGameSwipe()
    }

    override fun onDetachedFromWindow() {
        cancelGameSwipe()
        super.onDetachedFromWindow()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        if (inputEnabled && phase == GamePhase.Idle) {
            info.isScrollable = true
            info.addAction(if (selectedGame() == GameLine.Mai) AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD
                else AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        }
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (inputEnabled && phase == GamePhase.Idle) {
            val game = selectedGame()
            if ((action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD && game == GameLine.Mai) ||
                (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD && game == GameLine.Chu)) {
                position = endpoint(game)
                settleTo(if (game == GameLine.Mai) GameLine.Chu else GameLine.Mai)
                return true
            }
        }
        return super.performAccessibilityAction(action, arguments)
    }
}
