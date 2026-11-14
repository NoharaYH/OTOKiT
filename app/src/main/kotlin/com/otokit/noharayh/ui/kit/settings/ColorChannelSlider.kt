package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion

import com.otokit.noharayh.ui.constants.dimensions.ColorPickerDimensions as Dimensions
import com.otokit.noharayh.ui.animation.common.ContinuousCorners
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.withTranslation
import com.google.android.material.slider.LabelFormatter
import com.google.android.material.slider.Slider
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.PressHalo
import kotlin.math.abs
import kotlin.math.roundToInt

/** 保留官方 M3 拇指与无障碍；触摸采用相对拖动，外环与触觉由应用提供。 */
internal class ColorChannelSlider(context: Context) : Slider(ContextThemeWrapper(context, R.style.Theme_OTOKiT_ColorSlider)) {
    var onGrab: (() -> Unit)? = null
    var onRelease: (() -> Unit)? = null
    var onValue: ((Float) -> Unit)? = null
    private val density = resources.displayMetrics.density
    private val pressHalo = PressHalo(this)
    private val drag = RelativeSliderDrag(ViewConfiguration.get(context).scaledTouchSlop.toFloat())
    private val dragTicks = SliderDragTicks()
    private val haptics = SliderHaptics(this)
    private var touching = false
    private var activePointerId = MotionEvent.INVALID_POINTER_ID
    private var restingThumbWidth = 0
    private var ramp = IntArray(7) { 0xFF9E9E9E.toInt() }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stopPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPath = Path()
    private val trackRect = RectF()
    private var paintedThumbColor: Int? = null

    init {
        valueFrom = 0f
        valueTo = 1f
        stepSize = 0f
        labelBehavior = LabelFormatter.LABEL_GONE
        thumbWidth = dp(Dimensions.THUMB_WIDTH_DP)
        thumbHeight = dp(Dimensions.THUMB_HEIGHT_DP)
        thumbTrackGapSize = dp(Dimensions.TRACK_GAP_DP)
        trackHeight = dp(Dimensions.TRACK_HEIGHT_DP)
        trackInsideCornerSize = dp(Dimensions.TRACK_INNER_RADIUS_DP)
        trackStopIndicatorSize = 0
        // 官方 Slider 没有轨道 shader 入口；复刻轨道绘制，保留原控件拇指。
        trackActiveTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        trackInactiveTintList = ColorStateList.valueOf(Color.TRANSPARENT)
        // 官方组件内部的最小 thumb 热区为 48dp；外层整行另外提供 52dp 高度。
        minimumHeight = dp(Dimensions.TOUCH_TARGET_DP)
        haloRadius = 0
        thumbElevation = 0f
        setLabelFormatter { (it * 100).roundToInt().toString() }
        addOnChangeListener { _, current, fromUser ->
            // 键盘与无障碍 range action 仍由原 Slider 调值；展示动画不回写主题。
            if (fromUser) onValue?.invoke(current)
        }
    }

    fun renderRamp(colors: IntArray) {
        if (!ramp.contentEquals(colors)) {
            ramp = colors.copyOf()
            updateShaders()
            invalidate()
        }
        val position = value * (ramp.size - 1)
        val start = position.toInt().coerceIn(0, ramp.lastIndex)
        val color = ColorUtils.blendARGB(ramp[start], ramp[(start + 1).coerceAtMost(ramp.lastIndex)], position - start)
        if (paintedThumbColor != color) {
            paintedThumbColor = color
            thumbTintList = ColorStateList.valueOf(color)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateShaders()
    }

    private fun updateShaders() {
        if (trackWidth <= 0) return
        val colors = if (layoutDirection == LAYOUT_DIRECTION_RTL) ramp.reversedArray() else ramp
        val left = trackSidePadding.toFloat()
        val right = left + trackWidth
        activePaint.shader = LinearGradient(left, 0f, right, 0f, colors, null, Shader.TileMode.CLAMP)
        inactivePaint.shader = LinearGradient(left, 0f, right, 0f,
            IntArray(colors.size) { ColorUtils.blendARGB(colors[it], Color.WHITE, 0.3f) }, null, Shader.TileMode.CLAMP)
    }

    private fun drawTrackPart(canvas: Canvas, left: Float, right: Float, leftRadius: Float, rightRadius: Float, paint: Paint) {
        if (right <= left) return
        val half = trackHeight / 2f
        trackRect.set(left, height / 2f - half, right, height / 2f + half)
        ContinuousCorners.setCorners(trackPath, trackRect, leftRadius, rightRadius, rightRadius, leftRadius)
        canvas.drawPath(trackPath, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            finishTouch()
            return false
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                onGrab?.invoke()
                touching = true
                activePointerId = event.getPointerId(0)
                drag.begin(event.x, event.y, value, trackWidth.toFloat(), layoutDirection == LAYOUT_DIRECTION_RTL)
                dragTicks.begin(value, SystemClock.uptimeMillis())
                requestFocus()
                isPressed = true
                restingThumbWidth = thumbWidth
                // 与 M3 拇指按下时的半宽形状一致，外扩由已有弹簧驱动。
                thumbWidth = (restingThumbWidth * ColorPickerMotion.PRESSED_THUMB_WIDTH_FRACTION).roundToInt().coerceAtLeast(1)
                haptics.press()
                pressHalo.press()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!touching) return false
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) { finishTouch(); return false }
                val next = drag.move(event.getX(index), event.getY(index))
                when (drag.phase) {
                    RelativeSliderDrag.Phase.Dragging -> {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        updateFromDrag(next)
                    }
                    RelativeSliderDrag.Phase.Vertical -> finishTouch()
                    else -> Unit
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (touching && event.getPointerId(event.actionIndex) == activePointerId) {
                    val next = if (event.actionIndex == 0) 1 else 0
                    activePointerId = event.getPointerId(next)
                    drag.reanchor(event.getX(next), event.getY(next))
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!touching) return false
                val click = drag.phase == RelativeSliderDrag.Phase.Pressed
                val index = event.findPointerIndex(activePointerId)
                if (drag.phase == RelativeSliderDrag.Phase.Dragging && index >= 0) {
                    updateFromDrag(drag.move(event.getX(index), event.getY(index)))
                }
                finishTouch()
                haptics.release()
                if (click) performClick()
            }
            MotionEvent.ACTION_CANCEL -> finishTouch()
        }
        return true
    }

    private fun updateFromDrag(next: Float) {
        if (next == value) return
        value = next
        onValue?.invoke(next)
        if (dragTicks.move(next, trackWidth / density, SystemClock.uptimeMillis()) != null) haptics.tick()
    }

    private fun finishTouch(reset: Boolean = false, notifyRelease: Boolean = true) {
        haptics.cancel()
        val wasTouching = touching
        touching = false
        activePointerId = MotionEvent.INVALID_POINTER_ID
        drag.end()
        isPressed = false
        if (wasTouching) {
            thumbWidth = restingThumbWidth
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        if (reset) pressHalo.reset() else pressHalo.release()
        if (wasTouching && notifyRelease) onRelease?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        val fraction = if (layoutDirection == LAYOUT_DIRECTION_RTL) 1f - value else value
        val x = trackSidePadding + trackWidth * fraction
        val start = trackSidePadding.toFloat()
        val end = start + trackWidth
        val gap = thumbTrackGapSize + thumbWidth / 2f
        val outer = trackHeight / 2f
        val inner = trackInsideCornerSize.toFloat()
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        drawTrackPart(canvas, start, (x - gap).coerceAtLeast(start), outer, inner, if (rtl) inactivePaint else activePaint)
        drawTrackPart(canvas, (x + gap).coerceAtMost(end), end, inner, outer, if (rtl) activePaint else inactivePaint)
        val stopX = if (rtl) start + outer else end - outer
        if (abs(stopX - x) > gap + dp(Dimensions.STOP_SIZE_DP)) {
            stopPaint.color = ColorUtils.blendARGB(ramp.last(), Color.BLACK, 0.35f)
            canvas.drawCircle(stopX, height / 2f, dp(Dimensions.STOP_SIZE_DP) / 2f, stopPaint)
        }
        canvas.withTranslation(x - thumbWidth / 2f, (height - thumbHeight) / 2f) {
            pressHalo.draw(this, thumbWidth, thumbHeight, 0f, thumbWidth / 2f)
        }
        super.onDraw(canvas)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) finishTouch(reset = true)
    }

    override fun onDetachedFromWindow() {
        finishTouch(reset = true, notifyRelease = false)
        super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * density).roundToInt()
}
