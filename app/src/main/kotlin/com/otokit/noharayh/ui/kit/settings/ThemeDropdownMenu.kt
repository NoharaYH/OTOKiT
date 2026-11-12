package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.TextView
import com.otokit.noharayh.ui.animation.settings.ThemeMenuMotionRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.theme.ThemeOption
import kotlin.math.abs
import kotlin.math.roundToInt

/** 可挂载到卡片内部的菜单内容；不持有窗口、焦点或返回导航。 */
@android.annotation.SuppressLint("ViewConstructor") // 由面板提供选项和视口预算，不参与 XML inflate。
internal class ThemeDropdownMenu(
    context: Context,
    options: List<ThemeOption>,
    availableHeight: Int,
    onBottomRadiusChanged: () -> Unit,
    onSelected: (ThemeOption) -> Unit,
) : FrameLayout(context) {
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
    private val rowHeight = dp(Dimensions.MENU_ROW_DP).toFloat()
    private val space = availableHeight.coerceAtLeast(dp(Dimensions.MENU_ROW_DP + Dimensions.MENU_PADDING_DP * 2))
    private val previewScale = ((space - dp(Dimensions.MENU_PADDING_DP * 2) - rowHeight) /
        ((options.size - 1).coerceAtLeast(1) * dp(Dimensions.MENU_BAR_DP + Dimensions.MENU_GAP_DP).toFloat())).coerceIn(0f, 1f)
    private val gap = dp(Dimensions.MENU_GAP_DP) * previewScale
    private val collapsedHeight = dp(Dimensions.MENU_BAR_DP) * previewScale
    private val slots = ((space - dp(Dimensions.MENU_PADDING_DP * 2) - options.size * collapsedHeight -
        (options.size - 1) * gap) / (rowHeight - collapsedHeight))
        .toInt().coerceIn(1, options.size.coerceAtLeast(1)).coerceAtMost(Dimensions.MENU_MAX_SLOTS)
    private val geometry = ThemeMenuGeometry(options.size, slots, rowHeight, gap,
        collapsedHeight, dp(Dimensions.MENU_PADDING_DP).toFloat())
    val contentHeight = geometry.height.roundToInt()
    private val viewport = MenuViewport(context, options, geometry, onBottomRadiusChanged, onSelected)
    val bottomCornerRadius: Float get() = viewport.bottomCornerRadius
    init {
        clipChildren = false
        clipToPadding = false
        addView(viewport, LayoutParams(-1, -1))
    }
    fun setInteractionEnabled(enabled: Boolean) = viewport.setInteractionEnabled(enabled)
    fun scheduleOpeningHaptic(delay: Long) = viewport.scheduleOpeningHaptic(delay)
    fun stopMotion() = viewport.stopMotion()
}

/** 无 ScrollView 的单一运动容器：位置同时决定行高、文字显隐和吸附位置。 */
@android.annotation.SuppressLint("ViewConstructor") // 菜单只从选项及几何创建，不参与 XML inflate。
private class MenuViewport(
    context: Context,
    options: List<ThemeOption>,
    private val geometry: ThemeMenuGeometry,
    private val onBottomRadiusChanged: () -> Unit,
    onSelected: (ThemeOption) -> Unit,
) : ViewGroup(context) {
    private val edges = themeMenuEdges(options)
    private val density = resources.displayMetrics.density
    private val config = ViewConfiguration.get(context)
    private var interactionEnabled = false
    private var touching = false
    private var offset = 0f
    private var reportedBottomRadius = -1f
    /** 末项永远是分组尾部；返回实际高度约束后的 DMB 半径，不返回 22dp 的目标值。 */
    val bottomCornerRadius: Float get() {
        val expansion = geometry.expansion(childCount - 1, offset)
        val height = geometry.collapsedHeight + expansion * (geometry.rowHeight - geometry.collapsedHeight)
        return ((Dimensions.MENU_BAR_RADIUS_DP + (Dimensions.MENU_LARGE_RADIUS_DP -
            Dimensions.MENU_BAR_RADIUS_DP) * expansion) * density).coerceAtMost(height / 2f)
    }
    private var dragging = false
    private var interrupted = false
    private var downY = 0f
    private var lastY = 0f
    private var pointerId = -1
    private var tracker: VelocityTracker? = null
    private val expansionFeedback = ThemeMenuExpansionFeedback(geometry)
    private val openingHaptic = Runnable {
        if (canPlayHaptic()) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    private var frontIndex = -1
    private val labels = mutableListOf<TextView>()
    private val motion = ThemeMenuMotionRunner(density, geometry.stride, geometry.maximum,
        geometry::snap, ::updatePosition, ::layoutRows)

    init {
        clipChildren = false
        clipToPadding = false
        isChildrenDrawingOrderEnabled = true
        isFocusable = true
        options.forEachIndexed { index, option ->
            val label = TextView(context).apply {
                text = option.title
                textSize = 14f
                includeFontPadding = false
                gravity = Gravity.CENTER_VERTICAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                setTextColor(0xFF323232.toInt())
                AppTypography.applyAppTypeface(this)
            }
            labels += label
            addView(PressSurface(context).apply {
                fillColor = 0xFFF2F2F2.toInt()
                baseRadius = Dimensions.MENU_INNER_RADIUS_DP * density
                outerHaloEnabled = true
                deferClickUntilReleaseFeedback = true
                contentDescription = option.title
                setPadding((Dimensions.MENU_TEXT_INSET_DP * density).roundToInt(), 0, (Dimensions.MENU_TEXT_INSET_DP * density).roundToInt(), 0)
                addView(label, FrameLayout.LayoutParams(-1, -1))
                addOnPressStateChangedListener { pressed ->
                    if (pressed) { frontIndex = index; invalidate() }
                }
                setOnClickListener { if (!motion.isRunning && !dragging) onSelected(option) }
            })
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        layoutRows()
    }

    private fun updatePosition(value: Float) {
        offset = value
        // 动画回调直接更新当前帧，避免在 draw/computeScroll 中排下一次布局。
        if (width > 0) layoutRows()
        val expanded = expansionFeedback.update(offset)
        if (expanded && (dragging || motion.isRunning) && isAttachedToWindow && isShown && hasWindowFocus()) {
            // 和容器到达完整显示高度的这一帧同步，拖动、惯性和回弹都适用，不等弹簧结束。
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    private fun layoutRows() {
        val pad = geometry.padding.roundToInt()
        val overscroll = (offset - offset.coerceIn(0f, geometry.maximum)) * Spec.OVERSCROLL_SHIFT_FACTOR
        for (index in 0 until childCount) {
            val child = getChildAt(index) as PressSurface
            val (top, bottom) = geometry.bounds(index, offset)
            val h = (bottom - top).coerceAtLeast(0f)
            child.visibility = if (h < Dimensions.MENU_MIN_VISIBLE_DP * density) INVISIBLE else VISIBLE
            child.isEnabled = interactionEnabled && h >= geometry.rowHeight - density && !motion.isRunning && !dragging
            child.isFocusable = child.isEnabled
            child.importantForAccessibility = if (child.isEnabled) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            labels[index].alpha = ((h / geometry.rowHeight - Spec.LABEL_FADE_START) / (1f - Spec.LABEL_FADE_START)).coerceIn(0f, 1f)
            val outerRadius = (Dimensions.MENU_BAR_RADIUS_DP + (Dimensions.MENU_LARGE_RADIUS_DP -
                Dimensions.MENU_BAR_RADIUS_DP) * geometry.expansion(index, offset)) * density
            child.topCornerRadius = if (edges[index].top) outerRadius else Dimensions.MENU_INNER_RADIUS_DP * density
            child.bottomCornerRadius = if (edges[index].bottom) outerRadius else Dimensions.MENU_INNER_RADIUS_DP * density
            child.measure(MeasureSpec.makeMeasureSpec((width - 2 * pad).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h.roundToInt(), MeasureSpec.EXACTLY))
            child.layout(pad, (top + overscroll).roundToInt(), width - pad,
                (top + overscroll).roundToInt() + h.roundToInt())
            child.invalidate()
        }
        val radius = bottomCornerRadius
        if (radius != reportedBottomRadius) {
            reportedBottomRadius = radius
            onBottomRadiusChanged()
        }
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        if (frontIndex !in 0 until childCount) return drawingPosition
        return if (drawingPosition == childCount - 1) frontIndex
        else if (drawingPosition >= frontIndex) drawingPosition + 1 else drawingPosition
    }

    fun setInteractionEnabled(enabled: Boolean) {
        if (interactionEnabled == enabled) return
        interactionEnabled = enabled
        if (!enabled) stopMotion()
        layoutRows()
        if (enabled && offset != geometry.snap(offset)) snap()
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!interactionEnabled) return true
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            // 内嵌后必须在 DOWN 时声明菜单持有本次纵向手势，避免外层 ScrollView 抢先拦截。
            parent?.requestDisallowInterceptTouchEvent(true)
            interrupted = motion.isRunning
            stopMotion()
            touching = true
            pointerId = event.getPointerId(0)
            downY = event.y
            lastY = event.y
            dragging = false
            tracker = VelocityTracker.obtain()
            updatePosition(offset)
        }
        if (event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
            event.getPointerId(event.actionIndex) == pointerId) {
            // 拦截发生的这一条事件只会给旧子目标发送 CANCEL，不会再交给父 onTouchEvent。
            // 因此主指结束必须在分发入口处理，兼容尚未拖动、子卡片仍持有触摸的情况。
            dragging = false
            touching = false
            pointerId = MotionEvent.INVALID_POINTER_ID
            tracker?.recycle()
            tracker = null
            snap()
        }
        tracker?.addMovement(event)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            pointerId = MotionEvent.INVALID_POINTER_ID
            tracker?.recycle()
            tracker = null
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return interrupted
        // 主指离开就结束本次手势；拦截剩余指针，避免子项收到尾部 UP 而误点击。
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return true
        val index = event.findPointerIndex(pointerId)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && index >= 0 &&
            abs(event.getY(index) - downY) > config.scaledTouchSlop) {
            dragging = true
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // INVALID_POINTER_ID 为 -1，而 VelocityTracker 将 -1 解释为“当前活动指针”。
        // 已终止的主指不能借另一根手指的 UP 再次启动运动。
        if (pointerId == MotionEvent.INVALID_POINTER_ID) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val y = event.getY(index)
                val delta = lastY - y
                lastY = y
                dragging = true
                val outside = offset < 0f || offset > geometry.maximum
                updatePosition((offset + delta * if (outside) Spec.OVERSCROLL_DRAG_FACTOR else 1f)
                    .coerceIn(-Dimensions.MENU_OVERSCROLL_DP * density, geometry.maximum + Dimensions.MENU_OVERSCROLL_DP * density))
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !interrupted) performClick()
                dragging = false
                touching = false
                tracker?.computeCurrentVelocity(1000, config.scaledMaximumFlingVelocity.toFloat())
                val velocity = -(tracker?.getYVelocity(pointerId) ?: 0f)
                pointerId = MotionEvent.INVALID_POINTER_ID
                if (abs(velocity) > geometry.stride * Spec.FLING_HANDOFF_SPEED && offset in 0f..geometry.maximum && geometry.maximum > 0f) {
                    motion.startFling(offset, velocity)
                } else snap(velocity)
            }
            MotionEvent.ACTION_CANCEL -> {
                dragging = false
                touching = false
                pointerId = MotionEvent.INVALID_POINTER_ID
                tracker?.recycle()
                tracker = null
                snap()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    fun scheduleOpeningHaptic(delayMillis: Long) {
        removeCallbacks(openingHaptic)
        postDelayed(openingHaptic, delayMillis.coerceAtLeast(Spec.OPEN_HAPTIC_MIN_DELAY_MS))
    }

    private fun canPlayHaptic(): Boolean =
        interactionEnabled && !motion.isRunning && !touching && isAttachedToWindow && isShown && hasWindowFocus()

    private fun snap(velocity: Float = 0f, target: Float = geometry.snap(offset)) {
        if (touching) return
        motion.snap(offset, velocity, target)
    }

    fun stopMotion() {
        removeCallbacks(openingHaptic)
        motion.stop()
        dragging = false
        touching = false
        pointerId = MotionEvent.INVALID_POINTER_ID
        tracker?.recycle()
        tracker = null
    }

    override fun onDetachedFromWindow() {
        stopMotion()
        super.onDetachedFromWindow()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) {
            stopMotion()
            dragging = false
            updatePosition(geometry.snap(offset))
        }
    }

    override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
        super.onInitializeAccessibilityNodeInfo(info)
        info.isScrollable = geometry.maximum > 0f
        if (offset > 0f) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD)
        if (offset < geometry.maximum) info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD)
    }

    override fun performAccessibilityAction(action: Int, arguments: android.os.Bundle?): Boolean {
        if (!interactionEnabled) return false
        val step = when (action) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD -> geometry.stride
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD -> -geometry.stride
            else -> return super.performAccessibilityAction(action, arguments)
        }
        stopMotion()
        snap(target = (geometry.snap(offset) + step).coerceIn(0f, geometry.maximum))
        return true
    }
}
