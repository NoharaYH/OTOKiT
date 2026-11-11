package com.otokit.noharayh.ui.animation.chrome

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition

/** 标题文字的属性呈现与 ghost 所有者；只消费外部进度，不计时、不提交导航状态。 */
internal class TitleTextTransitionRunner(
    private val titleHost: FrameLayout,
    private val titleMotionHost: View,
    private val functionalHost: View,
    private val settingsColumn: View,
    private val maiStack: FrameLayout,
    private val chuStack: FrameLayout,
    private val refreshPivots: () -> Unit,
    private val refreshStackPivot: (FrameLayout) -> Unit,
    private val navigationBounds: () -> RectF?,
) {
    private val settingsOpticalY = settingsColumn.translationY
    private var disposed = false
    private var navigationActive = false
    var settingsTransitionActive = false
        private set
    private var ghost: ImageView? = null
    private var ghostStart = TransitionFrame(1f, 1f, 0f, 0f)
    var backPreviewActive = false
        private set

    fun beginBackPreview() {
        if (disposed) return
        cancelNavigation()
        backPreviewActive = true
        settingsTransitionActive = true
    }

    fun endBackPreview() {
        backPreviewActive = false
    }

    fun prepareSettingsTransition() {
        if (disposed) return
        cancelNavigation()
        backPreviewActive = false
        settingsTransitionActive = true
    }

    fun applyGameFrame(frame: GameFrame?, currentIsMai: Boolean) {
        if (disposed) return
        val progress = frame?.normalizedProgress
            ?: if (currentIsMai) 0f else 1f
        val phase = frame?.phase ?: GamePhase.Idle
        val selectedMai = currentIsMai || progress < 0.5f

        if (phase == GamePhase.Idle) {
            showStack(maiStack, if (selectedMai) View.VISIBLE else View.GONE)
            showStack(chuStack, if (selectedMai) View.GONE else View.VISIBLE)
            resetStack(maiStack)
            resetStack(chuStack)
            return
        }

        val width = titleHost.width.coerceAtLeast(1)
        val pair = TransitionFrames.pagerPair(
            kind = PagerPairTransition.KIND,
            progress = progress,
            slideDistancePx = width * PagerPairTransition.TRANSLATION_RATIO,
            minScale = PagerPairTransition.MIN_SCALE,
            minAlpha = PagerPairTransition.MIN_ALPHA,
        )
        applyStackFrame(maiStack, pair.first)
        applyStackFrame(chuStack, pair.second)
        maiStack.visibility = View.VISIBLE
        chuStack.visibility = View.VISIBLE
    }

    private fun showStack(stack: FrameLayout, visibility: Int) {
        stack.visibility = visibility
        if (visibility == View.VISIBLE) refreshStackPivot(stack)
    }

    private fun resetStack(stack: FrameLayout) {
        stack.alpha = 1f
        stack.scaleX = 1f
        stack.scaleY = 1f
        stack.translationX = 0f
        stack.translationY = 0f
    }

    private fun applyStackFrame(stack: FrameLayout, frame: com.otokit.noharayh.ui.animation.common.TransitionFrame) {
        refreshStackPivot(stack)
        stack.alpha = frame.alpha
        stack.scaleX = frame.scale
        stack.scaleY = frame.scale
        stack.translationX = frame.translationX
        stack.translationY = frame.translationY
    }

    fun settleSettings(settingsOpen: Boolean) =
        renderSettingsProgress(TransitionProgress(if (settingsOpen) 1f else 0f, running = false))

    fun renderSettingsProgress(frame: TransitionProgress) {
        if (disposed) return
        settingsTransitionActive = frame.running
        if (!frame.running) backPreviewActive = false
        val p = frame.value.coerceIn(0f, 1f)
        refreshPivots()
        val exit = TitleTransitionFrames.settingsFeature(p, functionalHost.height.toFloat())
        functionalHost.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        functionalHost.alpha = exit.alpha
        functionalHost.scaleX = exit.scale
        functionalHost.scaleY = exit.scale
        functionalHost.translationY = exit.translationY

        val enter = TitleTransitionFrames.settingsOverlay(p, settingsColumn.height.toFloat())
        settingsColumn.visibility = if (p <= 0f) View.INVISIBLE else View.VISIBLE
        settingsColumn.alpha = enter.alpha
        settingsColumn.scaleX = enter.scale
        settingsColumn.scaleY = enter.scale
        // column 的固定光学偏移属于静止基线，不能被动画位移覆盖。
        settingsColumn.translationY = settingsOpticalY + enter.translationY
    }

    /** 只准备新旧标题；计时由内容切页执行器唯一负责。 */
    fun prepareNavigation(translationY: Float, updateTitles: () -> Unit) {
        if (disposed) return
        val presented = TransitionFrame(
            titleMotionHost.alpha, titleMotionHost.scaleX,
            titleMotionHost.translationX, titleMotionHost.translationY,
        )
        cancelNavigation()
        ghostStart = presented
        ghost = captureGhost()
        updateTitles()
        navigationDistanceY = translationY
        navigationActive = ghost != null && ValueAnimator.areAnimatorsEnabled()
        if (navigationActive) renderNavigationProgress(TransitionProgress(0f, running = true))
        else cancelNavigation()
    }

    private var navigationDistanceY = 0f

    fun renderNavigationProgress(frame: TransitionProgress) {
        if (disposed || !navigationActive) return
        val entry = TitleTransitionFrames.navEntry(frame.value, navigationDistanceY)
        val exit = TitleTransitionFrames.navGhostExit(frame.value, -navigationDistanceY, ghostStart)
        titleMotionHost.alpha = entry.alpha
        titleMotionHost.translationY = entry.translationY
        titleMotionHost.scaleX = entry.scale
        titleMotionHost.scaleY = entry.scale
        ghost?.let {
            it.alpha = exit.alpha
            it.translationY = exit.translationY
            it.translationX = exit.translationX
            it.scaleX = exit.scale
            it.scaleY = exit.scale
        }
        if (!frame.running) cancelNavigation()
    }

    fun cancelNavigation() {
        navigationActive = false
        titleMotionHost.alpha = 1f
        titleMotionHost.translationY = 0f
        titleMotionHost.scaleX = 1f
        titleMotionHost.scaleY = 1f
        clearGhost()
    }

    @SuppressLint("RtlHardcoded") // LM 使用物理左侧坐标，与标题文字宿主一致。
    private fun captureGhost(): ImageView? {
        clearGhost()
        val bounds = navigationBounds() ?: return null
        val left = kotlin.math.floor(bounds.left).toInt()
        val top = kotlin.math.floor(bounds.top).toInt()
        val width = kotlin.math.ceil(bounds.right).toInt() - left
        val height = kotlin.math.ceil(bounds.bottom).toInt() - top
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        try {
            val canvas = Canvas(bitmap)
            canvas.translate(-left.toFloat(), -top.toFloat())
            titleMotionHost.draw(canvas)
        } catch (_: Throwable) {
            bitmap.recycle()
            return null
        }
        val image = ImageView(titleHost.context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            pivotX = bounds.left - left
            pivotY = bounds.centerY() - top
        }
        titleHost.addView(
            image,
            FrameLayout.LayoutParams(width, height).apply {
                gravity = Gravity.TOP or Gravity.LEFT
                leftMargin = titleMotionHost.left + left
                topMargin = titleMotionHost.top + top
            },
        )
        ghost = image
        return image
    }
    private fun clearGhost() {
        val current = ghost ?: return
        ghost = null
        titleHost.removeView(current)
        val drawable = current.drawable as? BitmapDrawable
        current.setImageDrawable(null)
        drawable?.bitmap?.recycle()
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        backPreviewActive = false
        cancelNavigation()
        settingsTransitionActive = false
    }

}
