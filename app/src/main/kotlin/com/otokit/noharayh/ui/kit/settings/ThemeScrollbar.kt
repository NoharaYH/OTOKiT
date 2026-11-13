package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.SeekBar
import com.otokit.noharayh.ui.animation.settings.ThemeScrollbarMotionRunner
import com.otokit.noharayh.ui.animation.settings.ThemePanelFrames
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.kit.common.PressSurface
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.pow

/** 宽命中区包住细滑块；反馈复用 PressSurface，拖动只改变菜单已有的滚动位置。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ThemeScrollbar(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDrag: (Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {
    private val density = resources.displayMetrics.density
    private val thumb = PressSurface(context).apply {
        fillColor = 0xFF757575.toInt()
        outerHaloEnabled = true
        pressInset = 0f
        whiteMaxAlpha = 0f
        rippleMaxDiameter = 0f
        rippleRestDiameter = 0f
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var trackTop = 0f
    private var trackBottom = 0f
    private var gapProgress = 0f
    private var fraction = 0f
    private var scrollable = false
    private var interactive = false
    private var started = false
    private var pull = 0f
    private var compression = 0f
    private data class Retraction(val progress: Float, val top: Float, val width: Float, val length: Float, val alpha: Float)
    private var retraction: Retraction? = null
    private var panelProgress = 0f
    private var pointer = MotionEvent.INVALID_POINTER_ID
    private var downY = 0f
    private var startFraction = 0f
    private var dragging = false
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val motion = ThemeScrollbarMotionRunner { excursion, squeeze ->
        pull = excursion
        compression = squeeze
        layoutThumb()
    }

    private val span: Float get() = (trackBottom - trackTop).coerceAtLeast(0f)
    private val restingLength: Float get() = themeScrollbarLength(span, Dimensions.FIELD_HEIGHT_DP * density, scrollable)
    private val travel: Float get() = (span - restingLength).coerceAtLeast(0f)

    init {
        clipChildren = false
        clipToPadding = false
        isFocusable = true
        contentDescription = context.getString(com.otokit.noharayh.R.string.settings_theme_selection)
        addView(thumb, LayoutParams(1, 1))
    }

    fun render(upperEnd: Float, lowerStart: Float, gap: Float, scrollFraction: Float,
        canScroll: Boolean, enabled: Boolean, progress: Float, retracting: Boolean) {
        panelProgress = progress
        val endGap = (Dimensions.SCROLLBAR_END_GAP_DP * density).coerceAtMost((lowerStart - upperEnd).coerceAtLeast(0f) / 2f)
        trackTop = upperEnd + endGap
        trackBottom = lowerStart - endGap
        gapProgress = gap
        fraction = scrollFraction
        scrollable = canScroll
        if (!enabled && interactive) cancelTouch()
        interactive = enabled
        importantForAccessibility = if (enabled && canScroll) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = enabled && canScroll
        retraction?.let {
            if (!retracting && progress >= it.progress) {
                retraction = null
                motion.resume()
            }
        }
        if (retraction != null) {
            layoutThumb()
            return
        }
        if (gap <= 0f) {
            started = false
            motion.reset()
        } else if (!started && span > 0f) {
            started = true
            motion.start()
        }
        layoutThumb()
    }

    fun beginRetraction(progress: Float) {
        if (retraction != null || thumb.parent == null || thumb.visibility != VISIBLE) return
        // 从当前实际形状接管，即使拉伸弹簧尚未结束，也不会先跳回稳定尺寸。
        retraction = Retraction(progress, thumb.translationY,
            thumb.width * thumb.scaleX, thumb.height * thumb.scaleY, thumb.alpha)
        motion.pause()
        cancelTouch()
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) = layoutThumb()

    private fun layoutThumb() {
        if (width == 0) return
        // 非线性横向挤压：回程逐渐鼓起，过平衡点时稍宽，再随同一弹簧恢复。
        var thickness = Dimensions.FIELD_STROKE_DP *
            (Dimensions.SCROLLBAR_WIDTH_DP / Dimensions.FIELD_STROKE_DP).pow(compression) * density
        var length = (restingLength + Dimensions.SCROLLBAR_STRETCH_DP * density * pull * gapProgress).coerceAtLeast(0f)
        var top = trackTop + travel * fraction + Dimensions.SCROLLBAR_PULL_DP * density * pull * gapProgress
        var opacity = gapProgress
        retraction?.let { from ->
            val remaining = 1f - ThemePanelFrames.scrollbarExit(from.progress, panelProgress)
            thickness = Dimensions.FIELD_STROKE_DP * density + (from.width - Dimensions.FIELD_STROKE_DP * density) * remaining
            length = from.length * remaining
            top = from.top
            opacity = from.alpha * remaining
        }
        if (opacity <= 0f || length <= 0f) {
            thumb.alpha = 0f
            thumb.visibility = INVISIBLE
            if (thumb.parent === this) removeView(thumb)
            return
        }
        if (thumb.parent == null) addView(thumb, LayoutParams(1, 1))
        thumb.alpha = opacity
        val w = ceil(thickness).toInt().coerceAtLeast(1)
        val h = ceil(length).toInt().coerceAtLeast(1)
        thumb.baseRadius = thickness / 2f
        thumb.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        val left = (width - w) / 2
        thumb.layout(left, 0, left + w, h)
        // 细条只有几个像素宽，整数布局会把弹性变成台阶；保留小数尺寸和居中位置。
        // 这只用于形成动画的几何精度，PressSurface 的按压内缩仍为零。
        thumb.pivotX = w / 2f
        thumb.pivotY = 0f
        thumb.scaleX = thickness / w
        thumb.scaleY = length / h
        thumb.translationX = (width - w) / 2f - left
        thumb.translationY = top
        thumb.visibility = VISIBLE
        invalidate()
    }

    // 将反馈触点固定到滑块内部；滑块随拖动移动时不会因手指离开旧边界而取消抬手反馈。
    private fun feedback(event: MotionEvent, action: Int) {
        val copy = MotionEvent.obtain(event)
        copy.action = action
        copy.setLocation(thumb.width / 2f, thumb.height / 2f)
        thumb.onTouchEvent(copy)
        copy.recycle()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // Android 推荐至少 48×48dp；命中范围独立于可见滑块的宽度和长度。
            val hitHeight = maxOf(Dimensions.SCROLLBAR_HIT_WIDTH_DP * density, thumb.height.toFloat())
            val centerY = thumb.translationY + thumb.height / 2f
            if (!interactive || span <= 0f || event.x < 0f || event.x > width ||
                event.y < centerY - hitHeight / 2f || event.y > centerY + hitHeight / 2f) return false
            parent?.requestDisallowInterceptTouchEvent(true)
            pointer = event.getPointerId(0)
            downY = event.y
            dragging = false
            motion.finish()
            if (scrollable) onDragStart()
            startFraction = fraction
            feedback(event, MotionEvent.ACTION_DOWN)
            return true
        }
        if (pointer == MotionEvent.INVALID_POINTER_ID) return false
        val index = event.findPointerIndex(pointer)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (index < 0) { cancelTouch(); return true }
                val delta = event.getY(index) - downY
                if (abs(delta) > slop) dragging = true
                if (dragging && scrollable && travel > 0f) onDrag((startFraction + delta / travel).coerceIn(0f, 1f))
            }
            MotionEvent.ACTION_UP -> {
                feedback(event, MotionEvent.ACTION_UP)
                finishTouch()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
            MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointer) cancelTouch()
        }
        return true
    }

    private fun finishTouch() {
        pointer = MotionEvent.INVALID_POINTER_ID
        dragging = false
        parent?.requestDisallowInterceptTouchEvent(false)
        if (scrollable) onDragEnd()
    }

    private fun cancelTouch() {
        if (pointer == MotionEvent.INVALID_POINTER_ID) return
        val event = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        feedback(event, MotionEvent.ACTION_CANCEL)
        event.recycle()
        finishTouch()
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.className = SeekBar::class.java.name
        if (!scrollable || !interactive) return
        info.isScrollable = true
        info.rangeInfo = AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_FLOAT, 0f, 1f, fraction)
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS)
        if (fraction > 0f) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (fraction < 1f) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (!interactive || !scrollable) return super.performAccessibilityAction(action, arguments)
        val target = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> fraction + 0.2f
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> fraction - 0.2f
            android.R.id.accessibilityActionSetProgress -> {
                val key = AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE
                if (arguments?.containsKey(key) != true) return false
                arguments.getFloat(key)
            }
            else -> return super.performAccessibilityAction(action, arguments)
        }
        if (!target.isFinite()) return false
        onDragStart()
        onDrag(target.coerceIn(0f, 1f))
        onDragEnd()
        return true
    }

    fun reset() {
        cancelTouch()
        retraction = null
        gapProgress = 0f
        started = false
        motion.reset()
        if (thumb.parent === this) removeView(thumb)
    }
    override fun onDetachedFromWindow() { reset(); super.onDetachedFromWindow() }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) { cancelTouch(); motion.finish() }
    }
}

internal fun themeScrollbarLength(trackLength: Float, fieldHeight: Float, scrollable: Boolean): Float =
    if (scrollable) fieldHeight.coerceIn(0f, trackLength.coerceAtLeast(0f)) else trackLength.coerceAtLeast(0f)
