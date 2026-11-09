package com.otokit.noharayh.ui.chrome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageButton
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.animation.chrome.TitleButtonFeedback
import com.otokit.noharayh.ui.animation.chrome.TitleButtonPressSpec
import kotlin.math.min

/** 本体按下缩 3dp，黑色外扩由标题行内的独立视觉层绘制；不绘制发白覆盖。 */
internal class TitleBarPrimaryButton(context: Context) : AppCompatImageButton(context) {
    var onFeedbackFrame: (() -> Unit)? = null
    val feedback = TitleButtonFeedback(this) { invalidate(); onFeedbackFrame?.invoke() }

    init {
        // 反馈全部由本类绘制；关闭系统焦点高亮，避免恢复焦点时出现常驻灰环。
        background = null
        defaultFocusHighlightEnabled = false
        // 关闭平台按钮的按压 elevation 动画；否则会与黑色 halo 叠出第二层深灰环。
        stateListAnimator = null
        elevation = 0f
        translationZ = 0f
    }

    private val pressInsetPx = TitleButtonPressSpec.INSET_DP * resources.displayMetrics.density
    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 圆形底色；由标题栏按主题写入。 */
    var circleColor: Int = Color.GRAY
        set(value) { field = value; invalidate() }
    private val scaleHolder = FloatValueHolder(0f)
    private val scaleAnim = SpringAnimation(scaleHolder).apply {
        setSpring(SpringForce(0f).setDampingRatio(TitleButtonPressSpec.PRESS_DAMPING)
            .setStiffness(TitleButtonPressSpec.PRESS_STIFFNESS))
        setMinimumVisibleChange(TitleButtonPressSpec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ -> applyPressScale() }
    }
    private var activePointer = MotionEvent.INVALID_POINTER_ID
    private var tracking = false
    private var inputClick = false
    private var keyPress = false

    private fun inside(x: Float, y: Float) = x in 0f..width.toFloat() && y in 0f..height.toFloat()

    private fun startPress() {
        feedback.press()
        scaleAnim.animateToFinalPosition(1f)
    }

    /** 只重绘圆底，不改 View 变换：缩放 View 会与 halo 叠出深灰环。 */
    private fun applyPressScale() {
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility") // 有效 UP 使用 performClick，保留按钮语义。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !isClickable) { cancelFeedback(); return false }
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointer = event.getPointerId(0)
                tracking = true
                isPressed = true
                startPress()
            }
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointer)
                if (tracking && (index < 0 || !inside(event.getX(index), event.getY(index)))) end(false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) end(false)
            }
            MotionEvent.ACTION_UP -> end(tracking && inside(event.x, event.y))
            MotionEvent.ACTION_CANCEL -> end(false)
        }
        return true
    }

    private fun end(clicked: Boolean) {
        tracking = false
        keyPress = false
        activePointer = MotionEvent.INVALID_POINTER_ID
        isPressed = false
        feedback.release(clicked)
        scaleAnim.animateToFinalPosition(0f)
        if (clicked) {
            inputClick = true
            try { performClick() } finally { inputClick = false }
        }
    }

    private fun isActivation(keyCode: Int) = keyCode == KeyEvent.KEYCODE_ENTER ||
        keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_SPACE

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!isActivation(keyCode) || !isEnabled) return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0 && !tracking) {
            keyPress = true
            tracking = true
            isPressed = true
            startPress()
        }
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!isActivation(keyCode)) return super.onKeyUp(keyCode, event)
        end(isEnabled && keyPress && !event.isCanceled)
        return true
    }

    override fun performClick(): Boolean {
        if (!isEnabled || !isClickable) return false
        // 无障碍/程序化点击没有 DOWN/UP，也提供相同的一次反馈。
        if (!inputClick) { startPress(); feedback.release(clicked = true); scaleAnim.animateToFinalPosition(0f) }
        return super.performClick()
    }

    override fun onDraw(canvas: Canvas) {
        val radius = min(width, height) / 2f - pressInsetPx * scaleHolder.value.coerceIn(0f, 1f)
        if (radius > 0f) {
            circlePaint.color = circleColor
            canvas.drawCircle(width / 2f, height / 2f, radius, circlePaint)
        }
        super.onDraw(canvas) // 图标保持原尺寸，只让圆底内收。
    }

    fun cancelFeedback() {
        tracking = false
        keyPress = false
        activePointer = MotionEvent.INVALID_POINTER_ID
        isPressed = false
        feedback.reset()
        scaleAnim.cancel()
        scaleHolder.value = 0f
        applyPressScale()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) cancelFeedback()
    }

    override fun onDetachedFromWindow() {
        cancelFeedback()
        super.onDetachedFromWindow()
    }
}
