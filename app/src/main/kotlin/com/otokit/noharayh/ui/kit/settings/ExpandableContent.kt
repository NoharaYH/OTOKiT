package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Canvas
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.graphics.withSave
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.ContainerMotion
import kotlin.math.roundToInt

/** 内容槽位负责测量、入退场和输入门控，页面只设置内容与展开状态。 */
internal class ExpandableContent(context: Context) : FrameLayout(context) {
    private var content: View? = null
    private var contentAlpha = 1f
    private var contentTranslationY = 0f
    var progress = 1f
        private set
    var onProgress: ((Float) -> Unit)? = null
    var controlsEnabled = true
        set(value) {
            if (field == value) return
            field = value
            updateInputGate()
        }
    private var inputAllowed = true
    private var touchActive = false
    private var expanded = true
    private val transition = ContentTransition(1f, { a, b, t -> a + (b - a) * t }, ::render)
    private var pendingContent: View? = null
    private var replacing = false

    init { clipChildren = false; clipToPadding = false }

    fun setContent(view: View?) {
        transition.stop(); replacing = false; pendingContent = null
        install(view)
        render(if (expanded) 1f else 0f)
    }

    /** 替换先退出旧内容再进入新内容；中途再次替换只保留最新目标。 */
    fun replaceContent(view: View?, animate: Boolean = true) {
        if (!animate || !isAttachedToWindow || !expanded || content == null) { setContent(view); return }
        pendingContent = view; replacing = true
        transition.play(0f, from = progress)
    }

    fun setExpanded(value: Boolean, animate: Boolean = true) {
        if (replacing) { install(pendingContent); pendingContent = null; replacing = false }
        expanded = value
        transition.play(if (value) 1f else 0f, animate && isAttachedToWindow, progress)
    }

    /** 供标准组合组件共享外部过渡进度；页面应使用 setExpanded。 */
    fun renderProgress(value: Float) {
        transition.stop()
        expanded = value > 0f
        render(value)
    }

    private fun install(view: View?) {
        val previous = content
        removeAllViews()
        // 只撤销槽位施加的表现，归还调用方的原始值，允许旧内容重新装配。
        previous?.apply { alpha = contentAlpha; translationY = contentTranslationY }
        content = view
        contentAlpha = view?.alpha ?: 1f
        contentTranslationY = view?.translationY ?: 0f
        if (view != null) addView(view, LayoutParams(-1, -2))
    }

    private fun render(value: Float) {
        progress = value.coerceIn(0f, 1f)
        if (replacing && progress == 0f && !transition.isRunning) {
            val next = pendingContent
            pendingContent = null; replacing = false
            install(next)
            transition.play(1f, isAttachedToWindow, 0f)
            return
        }
        content?.apply {
            alpha = contentAlpha * progress
            translationY = contentTranslationY - ContainerMotion.CONTENT_SHIFT_DP * resources.displayMetrics.density * (1f - progress)
        }
        updateInputGate()
        onProgress?.invoke(progress)
        requestLayout(); invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 内容始终按自然高度测量，再缩小槽位；不把动画中的高度反馈给子组件。
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
        setMeasuredDimension(measuredWidth, (measuredHeight * progress).roundToInt())
    }

    override fun dispatchDraw(canvas: Canvas) {
        canvas.withSave {
            if (progress < 1f) clipRect(0, 0, width, height)
            super.dispatchDraw(this)
        }
    }

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (!inputAllowed) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> touchActive = true
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> touchActive = false
        }
        return super.dispatchTouchEvent(event)
    }

    private fun updateInputGate() {
        val allowed = controlsEnabled && progress == 1f && !transition.isRunning
        importantForAccessibility = if (allowed) IMPORTANT_FOR_ACCESSIBILITY_AUTO
            else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        descendantFocusability = if (allowed) FOCUS_BEFORE_DESCENDANTS else FOCUS_BLOCK_DESCENDANTS
        if (inputAllowed == allowed) return
        inputAllowed = allowed
        if (!allowed) {
            // 先结束已交给后代的手势，不能只吞掉后续 UP，让后代停留在按下状态。
            if (touchActive) {
                touchActive = false
                val now = SystemClock.uptimeMillis()
                val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
                try { super.dispatchTouchEvent(cancel) } finally { cancel.recycle() }
            }
            content?.clearFocus()
            cancelPendingInputEvents()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean =
        if (inputAllowed) super.dispatchKeyEvent(event) else true

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean =
        if (inputAllowed) super.dispatchGenericMotionEvent(event) else true

    override fun onDetachedFromWindow() {
        transition.stop()
        if (replacing) { install(pendingContent); pendingContent = null; replacing = false }
        render(if (expanded) 1f else 0f)
        super.onDetachedFromWindow()
    }
}
