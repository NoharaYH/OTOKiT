package com.otokit.noharayh.ui.animation.shell

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionProgress
import com.otokit.noharayh.ui.animation.common.TransitionTiming
import com.otokit.noharayh.ui.constants.motion.SettingsTransition

/**
 * 设置覆盖层的进出与预测预览执行器。
 *
 * 只操作功能根和设置根的 alpha/scale，以及关闭时的纯视觉 ghost；
 * 不决定 settingsOpen、不改变输入/焦点隔离，也不持有导航状态。
 */
class SettingsTransitionRunner(
    private val overlayHost: FrameLayout,
    private val featureProvider: () -> View?,
    private val settingsProvider: () -> View?,
    private val onProgress: (TransitionProgress) -> Unit,
) {

    private var animator: ValueAnimator? = null
    private var ghost: ImageView? = null
    private var token = 0
    private var progress = 0f
    private var pendingLayout: Pair<android.view.ViewTreeObserver, android.view.ViewTreeObserver.OnGlobalLayoutListener>? = null

    /** 当前呈现进度：0=功能态，1=设置态；关闭接续预览的最后一帧。 */
    val openProgress: Float get() = progress

    fun openImmediate() {
        token++
        cancelAnimator()
        clearGhost()
        applySettled(1f)
    }

    fun animateOpen() {
        token++
        val generation = token
        cancelAnimator()
        clearGhost()
        // GONE 的 overlay 还没测量，直接播会因 height=0 被当成“立即完成”而硬切。
        if (overlayHost.height <= 0) {
            val observer = overlayHost.viewTreeObserver
            val listener = object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    if (pendingLayout?.second !== this) return
                    clearPendingLayout()
                    if (generation != token) return
                    if (overlayHost.height > 0) {
                        animateOpen()
                    } else {
                        applySettled(1f)
                        featureProvider()?.visibility = View.GONE
                    }
                }
            }
            pendingLayout = observer to listener
            observer.addOnGlobalLayoutListener(listener)
            overlayHost.requestLayout()
            return
        }
        animateTo(1f) {
            featureProvider()?.visibility = View.GONE
        }
    }

    /** 关闭前截取设置根的纯视觉 ghost，真实设置根立即退出交互。 */
    fun animateClose(fromProgress: Float, onClosed: () -> Unit) {
        token++
        val runToken = token
        cancelAnimator()
        clearGhost()
        progress = fromProgress.coerceIn(0f, 1f)
        if (progress <= 0f) {
            applySettled(0f)
            onClosed()
            return
        }
        val settings = settingsProvider() ?: run {
            applySettled(0f)
            onClosed()
            return
        }
        val captured = capture(settings)
        settings.visibility = View.GONE
        if (captured == null || !ValueAnimator.areAnimatorsEnabled()) {
            clearGhost()
            applySettled(0f)
            onClosed()
            return
        }
        featureProvider()?.visibility = View.VISIBLE
        applyOpenState(progress, ghostVisible = true)
        animator = ValueAnimator.ofFloat(progress, 0f).apply {
            duration = TransitionTiming.remainingDuration(SettingsTransition.EXIT_DURATION_MS, progress, 0f)
            interpolator = MotionInterpolators.of(SettingsTransition.CURVE)
            addUpdateListener { animation ->
                applyOpenState(animation.animatedValue as Float, ghostVisible = true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != runToken) return
                    animator = null
                    clearGhost()
                    applySettled(0f)
                    onClosed()
                }
            })
            start()
        }
    }

    /** 预测返回进度：0 = 完全设置态，1 = 完全功能态，不提交状态。 */
    fun preview(value: Float) {
        token++
        cancelAnimator()
        clearGhost()
        applyOpenState(1f - value.coerceIn(0f, 1f), ghostVisible = false)
    }

    /** 取消从当前已呈现帧回到设置态，标题共用同一条回弹进度。 */
    fun cancelPreview() {
        token++
        cancelAnimator()
        animateTo(1f)
    }

    fun endPreview(settingsOpen: Boolean) {
        token++
        cancelAnimator()
        clearGhost()
        if (settingsOpen) {
            applySettled(1f)
        } else {
            applySettled(0f)
        }
    }

    /** 冷启动/恢复后强制回到已关闭态；必须清零 progress，避免下次打开从 1 开始瞬间完成。 */
    fun resetClosed() {
        token++
        cancelAnimator()
        clearGhost()
        applySettled(0f)
    }
    fun cancel() {
        token++
        cancelAnimator()
        clearGhost()
    }

    fun dispose() {
        cancel()
        featureProvider()?.let {
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
            it.translationX = 0f
            it.translationY = 0f
        }
    }

    private fun animateTo(target: Float) {
        animateTo(target) {}
    }

    private fun animateTo(target: Float, onEnd: () -> Unit) {
        token++
        val runToken = token
        val start = progress
        if (!ValueAnimator.areAnimatorsEnabled() || overlayHost.height <= 0) {
            applySettled(target)
            onEnd()
            return
        }
        animator = ValueAnimator.ofFloat(start, target).apply {
            duration = TransitionTiming.remainingDuration(SettingsTransition.ENTER_DURATION_MS, start, target)
            interpolator = MotionInterpolators.of(SettingsTransition.CURVE)
            addUpdateListener { animation ->
                applyOpenState(animation.animatedValue as Float, ghostVisible = false)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != runToken) return
                    animator = null
                    applySettled(target)
                    onEnd()
                }
            })
            start()
        }
    }

    private fun applySettled(value: Float) {
        applyOpenState(value, ghostVisible = false, running = false)
        featureProvider()?.visibility = if (value >= 1f) View.GONE else View.VISIBLE
        settingsProvider()?.visibility = if (value >= 1f) View.VISIBLE else View.GONE
    }

    private fun applyOpenState(value: Float, ghostVisible: Boolean, running: Boolean = true) {
        progress = value
        featureProvider()?.let { view ->
            applyFrame(view, SettingsTransitionFrames.feature(value, presentationHeight(view).toFloat()))
        }
        // 真实设置与退场截图使用同一空间规则。
        val presentedSettings = if (ghostVisible) ghost else settingsProvider()
        presentedSettings?.let { view ->
            applyFrame(view, SettingsTransitionFrames.overlay(value, presentationHeight(view).toFloat()))
        }
        onProgress(TransitionProgress(value, running))
    }

    private fun applyFrame(view: View, frame: TransitionFrame) {
        view.visibility = View.VISIBLE
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
        view.pivotX = (view.width.takeIf { it > 0 } ?: view.layoutParams.width.coerceAtLeast(0)) / 2f
        view.pivotY = presentationHeight(view) / 2f
    }

    // 新截图尚未 layout 时，显式像素尺寸就是其局部几何，不能临时绕 (0, 0) 缩放。
    private fun presentationHeight(view: View): Int =
        view.height.takeIf { it > 0 } ?: view.layoutParams.height.coerceAtLeast(0)

    private fun capture(view: View): ImageView? {
        val width = view.width
        val height = view.height
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        try {
            view.draw(Canvas(bitmap))
        } catch (_: Throwable) {
            bitmap.recycle()
            return null
        }
        val image = ImageView(overlayHost.context).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            pivotX = width / 2f
            pivotY = height / 2f
        }
        overlayHost.addView(
            image,
            FrameLayout.LayoutParams(width, height),
        )
        ghost = image
        return image
    }

    private fun clearGhost() {
        val current = ghost ?: return
        ghost = null
        overlayHost.removeView(current)
        val drawable = current.drawable as? BitmapDrawable
        current.setImageDrawable(null)
        drawable?.bitmap?.recycle()
    }

    private fun cancelAnimator() {
        clearPendingLayout()
        val previous = animator
        animator = null
        previous?.cancel()
    }

    private fun clearPendingLayout() {
        val (observer, listener) = pendingLayout ?: return
        pendingLayout = null
        if (observer.isAlive) observer.removeOnGlobalLayoutListener(listener)
        // ViewTreeObserver 可能在 attach 时合并进新实例，移除宿主当前持有的监听。
        val current = overlayHost.viewTreeObserver
        if (current !== observer && current.isAlive) current.removeOnGlobalLayoutListener(listener)
    }
}
