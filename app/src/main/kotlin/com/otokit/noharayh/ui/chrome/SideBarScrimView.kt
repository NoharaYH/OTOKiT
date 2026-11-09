package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import kotlin.math.roundToInt

/** 固定左黑右透明的模态遮罩，整体不透明度随抽屉进度线性浮现。 */
class SideBarScrimView(context: Context) : View(context) {

    private val scrimPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var progress = 0f
    private var inputBlocked = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var clickCandidate = false

    init {
        isClickable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    fun setProgress(value: Float) {
        val bounded = value.coerceIn(0f, 1f)
        if (bounded == progress) return
        progress = bounded
        invalidate()
    }

    /** 视觉透明并不代表已经解除模态，例如打开首帧或正在取消旧触摸流。 */
    fun setInputBlocked(blocked: Boolean) {
        inputBlocked = blocked
        if (!blocked) {
            clickCandidate = false
            isPressed = false
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        scrimPaint.shader = LinearGradient(
            0f,
            0f,
            w.toFloat(),
            0f,
            0xB3000000.toInt(),
            0x00000000,
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0 || progress <= 0f) return
        // 进度只乘一次；shader 已包含完全展开时左端约70%的黑色强度。
        scrimPaint.alpha = (255 * progress).roundToInt()
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)
    }

    /**
     * 遮罩整片可点，但拖动不等于点击：超过 slop 就取消点击，仍由遮罩消费，避免点穿。
     * 开合结束且旧触摸流已清理、解除模态后才让事件落到内容场景。
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!inputBlocked) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                clickCandidate = true
                isPressed = true
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (clickCandidate &&
                    (abs(event.x - downX) > touchSlop || abs(event.y - downY) > touchSlop)
                ) {
                    clickCandidate = false
                    isPressed = false
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (clickCandidate) performClick()
                clickCandidate = false
                isPressed = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                clickCandidate = false
                isPressed = false
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
