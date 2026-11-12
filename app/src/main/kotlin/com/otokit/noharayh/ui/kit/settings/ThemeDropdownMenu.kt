package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.core.graphics.drawable.toDrawable
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.theme.ThemeOption
import kotlin.math.abs
import kotlin.math.roundToInt

/** 每个 DMI 拥有自己的菜单；关闭后释放宿主监听，不以全局对象持有 Activity。 */
internal class ThemeDropdownMenu private constructor(
    private val anchor: View,
    options: List<ThemeOption>,
    openingHapticDelayMillis: Long,
    private val onSelected: (ThemeOption) -> Unit,
    private val onDismiss: () -> Unit,
) {
    private var closing = false
    private var closed = false
    private var allowDismiss = false
    private val frame = Rect().also(anchor::getWindowVisibleDisplayFrame)
    private val location = IntArray(2).also(anchor::getLocationOnScreen)
    private val below = frame.bottom - location[1] - anchor.height - dp(8)
    private val above = location[1] - frame.top - dp(8)
    private val openBelow = below >= minOf(dp(220), above)
    private val space = (if (openBelow) below else above).coerceAtLeast(1)
    private val rowHeight = dp(44).toFloat()
    // 即使窗口很矮，也给每个折叠项目保留等高、等间距的薄条。
    private val previewScale = ((space - dp(16) - rowHeight) /
        ((options.size - 1).coerceAtLeast(1) * dp(10).toFloat())).coerceIn(0f, 1f)
    private val gap = dp(4) * previewScale
    private val collapsedHeight = dp(6) * previewScale
    private val slots = ((space - dp(16) - options.size * collapsedHeight -
        (options.size - 1) * gap) / (rowHeight - collapsedHeight))
        .toInt().coerceIn(1, options.size.coerceAtLeast(1)).coerceAtMost(6)
    private val geometry = ThemeMenuGeometry(options.size, slots, rowHeight, gap,
        collapsedHeight, dp(8).toFloat())
    private val viewport = MenuViewport(anchor.context, options, geometry) { option ->
        if (!closing && !closed) {
            // 先关闭菜单，再提交，允许提交同步禁用触发框。
            dismiss()
            onSelected(option)
        }
    }
    private val popup = object : PopupWindow(
        viewport, anchor.width, minOf(space, geometry.height.roundToInt()), true,
    ) {
        override fun dismiss() {
            if (allowDismiss) super.dismiss() else this@ThemeDropdownMenu.dismiss()
        }
    }
    private val attachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = Unit
        override fun onViewDetachedFromWindow(v: View) = dismiss(immediate = true)
    }
    private val layoutListener = View.OnLayoutChangeListener { _, l, t, r, b, ol, ot, or, ob ->
        if (l != ol || t != ot || r != or || b != ob) dismiss(immediate = true)
    }

    init {
        popup.apply {
            isOutsideTouchable = true
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            elevation = 0f
            animationStyle = 0
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            setOnDismissListener { release() }
        }
        viewport.isFocusableInTouchMode = true
        viewport.setOnKeyListener { _, code, event ->
            if (code == KeyEvent.KEYCODE_BACK) {
                if (event.action == KeyEvent.ACTION_UP) dismiss()
                true
            } else false
        }
        anchor.addOnAttachStateChangeListener(attachListener)
        anchor.addOnLayoutChangeListener(layoutListener)
        viewport.alpha = 0f
        viewport.translationY = dp(if (openBelow) -8 else 8).toFloat()
        popup.showAsDropDown(anchor, 0,
            if (openBelow) dp(4) else -anchor.height - popup.height - dp(4))
        viewport.requestFocus()
        viewport.animate().alpha(1f).translationY(0f).setDuration(180L).start()
        // Popup 已取得焦点后播放，并避开触发框仍在播放的按下反馈。
        viewport.scheduleOpeningHaptic(openingHapticDelayMillis)
    }

    fun dismiss(immediate: Boolean = false) {
        if (closed) return
        if (immediate) {
            viewport.animate().cancel()
            finishDismiss()
        } else if (!closing) {
            closing = true
            viewport.stopMotion()
            viewport.animate().alpha(0f).translationY(dp(if (openBelow) -8 else 8).toFloat())
                .setDuration(140L).withEndAction { finishDismiss() }.start()
        }
    }

    private fun finishDismiss() {
        allowDismiss = true
        popup.dismiss()
        release()
    }

    private fun release() {
        if (closed) return
        closed = true
        viewport.stopMotion()
        viewport.animate().cancel()
        anchor.removeOnAttachStateChangeListener(attachListener)
        anchor.removeOnLayoutChangeListener(layoutListener)
        onDismiss()
    }

    private fun dp(value: Int) = (value * anchor.resources.displayMetrics.density + 0.5f).toInt()

    companion object {
        fun show(anchor: View, options: List<ThemeOption>, openingHapticDelayMillis: Long,
            onSelected: (ThemeOption) -> Unit,
            onDismiss: () -> Unit): ThemeDropdownMenu =
            ThemeDropdownMenu(anchor, options, openingHapticDelayMillis, onSelected, onDismiss)
    }
}

/** 无 ScrollView 的单一运动容器：位置同时决定行高、文字显隐和吸附位置。 */
@android.annotation.SuppressLint("ViewConstructor") // 菜单只从选项及几何创建，不参与 XML inflate。
private class MenuViewport(
    context: Context,
    options: List<ThemeOption>,
    private val geometry: ThemeMenuGeometry,
    onSelected: (ThemeOption) -> Unit,
) : ViewGroup(context) {
    private val density = resources.displayMetrics.density
    private val config = ViewConfiguration.get(context)
    private enum class Motion { IDLE, DRAGGING, FLINGING, SETTLING }
    private var motion = Motion.IDLE
    private var touching = false
    private val position = FloatValueHolder(0f)
    private var offset = 0f
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
    private val spring = SpringAnimation(position).apply {
        spring = SpringForce(0f).setDampingRatio(0.82f).setStiffness(240f)
        setMinimumVisibleChange(0.2f * density)
        addUpdateListener { _, value, _ -> updatePosition(value) }
        addEndListener { _, canceled, _, _ ->
            if (!canceled) {
                finishSettling()
            }
        }
    }
    private val fling = FlingAnimation(position).apply {
        friction = FLING_FRICTION
        setMinValue(0f)
        setMaxValue(geometry.maximum)
        addUpdateListener { _, value, velocity ->
            updatePosition(value)
            val remaining = if (velocity > 0) geometry.maximum - value else value
            // 在惯性还没停下时接入吸附，并传递速度；只有一个动画可以写位置。
            if (abs(velocity) < geometry.stride * 3f || remaining < geometry.stride * 0.6f) {
                cancel()
                snap(velocity, geometry.snap(value + velocity / FLING_DECAY))
            }
        }
        addEndListener { _, canceled, _, velocity ->
            if (!canceled && motion == Motion.FLINGING) snap(velocity)
        }
    }

    private companion object {
        const val FLING_FRICTION = 1.8f
        // AndroidX FlingAnimation 的指数衰减系数。
        const val FLING_DECAY = 4.2f * FLING_FRICTION
    }

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
                baseRadius = 4 * density
                outerHaloEnabled = true
                deferClickUntilReleaseFeedback = true
                contentDescription = option.title
                setPadding((16 * density).roundToInt(), 0, (16 * density).roundToInt(), 0)
                addView(label, FrameLayout.LayoutParams(-1, -1))
                addOnPressStateChangedListener { pressed ->
                    if (pressed) { frontIndex = index; invalidate() }
                }
                setOnClickListener { if (motion == Motion.IDLE && !dragging) onSelected(option) }
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
        if (expanded && motion != Motion.IDLE && isAttachedToWindow && isShown && hasWindowFocus()) {
            // 和容器到达完整显示高度的这一帧同步，拖动、惯性和回弹都适用，不等弹簧结束。
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        invalidate()
    }

    private fun layoutRows() {
        val pad = geometry.padding.roundToInt()
        val overscroll = (offset - offset.coerceIn(0f, geometry.maximum)) * -0.25f
        for (index in 0 until childCount) {
            val child = getChildAt(index) as PressSurface
            val (top, bottom) = geometry.bounds(index, offset)
            val h = (bottom - top).coerceAtLeast(0f)
            child.visibility = if (h < 2 * density) INVISIBLE else VISIBLE
            child.isEnabled = h >= geometry.rowHeight - density && motion == Motion.IDLE
            child.isFocusable = child.isEnabled
            child.importantForAccessibility = if (child.isEnabled) IMPORTANT_FOR_ACCESSIBILITY_YES else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            labels[index].alpha = ((h / geometry.rowHeight - 0.45f) / 0.55f).coerceIn(0f, 1f)
            val outerRadius = (3f + 19f * geometry.expansion(index, offset)) * density
            child.topCornerRadius = if (index == 0) outerRadius else 4 * density
            child.bottomCornerRadius = if (index == childCount - 1) outerRadius else 4 * density
            child.measure(MeasureSpec.makeMeasureSpec((width - 2 * pad).coerceAtLeast(0), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h.roundToInt(), MeasureSpec.EXACTLY))
            child.layout(pad, (top + overscroll).roundToInt(), width - pad,
                (top + overscroll).roundToInt() + h.roundToInt())
            child.invalidate()
        }
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        if (frontIndex !in 0 until childCount) return drawingPosition
        return if (drawingPosition == childCount - 1) frontIndex
        else if (drawingPosition >= frontIndex) drawingPosition + 1 else drawingPosition
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            interrupted = motion != Motion.IDLE
            stopMotion()
            touching = true
            pointerId = event.getPointerId(0)
            downY = event.y
            lastY = event.y
            dragging = false
            tracker = VelocityTracker.obtain()
            updatePosition(offset)
        }
        tracker?.addMovement(event)
        val handled = super.dispatchTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            touching = false
            tracker?.recycle()
            tracker = null
        }
        return handled
    }

    override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) return interrupted
        if (event.actionMasked == MotionEvent.ACTION_MOVE && abs(event.y - downY) > config.scaledTouchSlop) {
            dragging = true
            motion = Motion.DRAGGING
            parent?.requestDisallowInterceptTouchEvent(true)
        }
        return dragging
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(pointerId)
                if (index < 0) return true
                val y = event.getY(index)
                val delta = lastY - y
                lastY = y
                dragging = true
                motion = Motion.DRAGGING
                val outside = offset < 0f || offset > geometry.maximum
                updatePosition((offset + delta * if (outside) 0.3f else 1f)
                    .coerceIn(-32 * density, geometry.maximum + 32 * density))
            }
            MotionEvent.ACTION_UP -> {
                if (!dragging && !interrupted) performClick()
                dragging = false
                touching = false
                tracker?.computeCurrentVelocity(1000, config.scaledMaximumFlingVelocity.toFloat())
                val velocity = -(tracker?.getYVelocity(pointerId) ?: 0f)
                if (abs(velocity) > geometry.stride * 3f && offset in 0f..geometry.maximum && geometry.maximum > 0f) {
                    motion = Motion.FLINGING
                    fling.setStartValue(offset).setStartVelocity(velocity).start()
                    updatePosition(offset)
                } else snap(velocity)
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_UP -> {
                if (event.actionMasked == MotionEvent.ACTION_POINTER_UP &&
                    event.getPointerId(event.actionIndex) != pointerId) return true
                dragging = false
                touching = false
                pointerId = MotionEvent.INVALID_POINTER_ID
                snap()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    fun scheduleOpeningHaptic(delayMillis: Long) {
        removeCallbacks(openingHaptic)
        postDelayed(openingHaptic, delayMillis.coerceAtLeast(32L))
    }

    private fun canPlayHaptic(): Boolean =
        motion == Motion.IDLE && !touching && isAttachedToWindow && isShown && hasWindowFocus()

    private fun snap(velocity: Float = 0f, target: Float = geometry.snap(offset)) {
        if (touching) return
        motion = Motion.SETTLING
        spring.setStartValue(offset)
        spring.setStartVelocity(velocity.coerceIn(-geometry.stride * 6f, geometry.stride * 6f))
        spring.animateToFinalPosition(target)
        updatePosition(offset)
    }

    private fun finishSettling() {
        updatePosition(geometry.snap(offset))
        motion = Motion.IDLE
        layoutRows()
    }

    fun stopMotion() {
        removeCallbacks(openingHaptic)
        fling.cancel()
        spring.cancel()
        motion = Motion.IDLE
        touching = false
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
