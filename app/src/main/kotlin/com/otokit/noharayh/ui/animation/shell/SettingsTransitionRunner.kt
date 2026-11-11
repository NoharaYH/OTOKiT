package com.otokit.noharayh.ui.animation.shell

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import android.widget.FrameLayout
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.animation.common.TransitionFrame
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
) {

    private var animator: ValueAnimator? = null
    private var ghost: ImageView? = null
    private var token = 0
    private var progress = 0f
    private var previewProgress = 0f
    private var previewing = false
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
        previewing = false
        progress = fromProgress.coerceIn(0f, 1f)
        if (progress <= 0f) {
            applySettled(0f)
            onClosed()
            return
        }
        val settings = settingsProvider() ?: run {
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
            duration = (SettingsTransition.EXIT_DURATION_MS * progress).toLong().coerceAtLeast(1L)
            interpolator = MotionInterpolators.of(SettingsTransition.CURVE)
            addUpdateListener { animation ->
                applyOpenState(animation.animatedValue as Float, ghostVisible = true)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != runToken) return
                    animator = null
                    clearGhost()
                    featureProvider()?.let {
                        it.visibility = View.VISIBLE
                        it.alpha = 1f
                        it.scaleX = 1f
                        it.scaleY = 1f
                    }
                    settings.visibility = View.GONE
                    progress = 0f
                    onClosed()
                }
            })
            start()
        }
    }

    /** 预测返回进度：0 = 完全设置态，1 = 完全功能态，不提交状态。 */
    fun preview(value: Float) {
        previewing = true
        cancelAnimator()
        clearGhost()
        featureProvider()?.visibility = View.VISIBLE
        settingsProvider()?.visibility = View.VISIBLE
        previewProgress = value.coerceIn(0f, 1f)
        applyPreviewState(previewProgress)
    }

    fun cancelPreview() {
        token++
        val runToken = token
        previewing = false
        cancelAnimator()
        if (!ValueAnimator.areAnimatorsEnabled()) {
            applySettled(1f)
            return
        }
        val start = previewProgress
        animator = ValueAnimator.ofFloat(start, 0f).apply {
            duration = (SettingsTransition.ENTER_DURATION_MS * start).toLong().coerceAtLeast(1L)
            interpolator = MotionInterpolators.of(SettingsTransition.CURVE)
            addUpdateListener { animation ->
                previewProgress = animation.animatedValue as Float
                applyPreviewState(previewProgress)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (token != runToken || animator !== animation) return
                    animator = null
                    applySettled(1f)
                }
            })
            start()
        }
    }

    fun endPreview(settingsOpen: Boolean) {
        token++
        previewing = false
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
        previewing = false
        applySettled(0f)
    }
    fun cancel() {
        token++
        cancelAnimator()
        clearGhost()
        previewing = false
    }

    fun dispose() {
        cancel()
        featureProvider()?.let {
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
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
            duration = (SettingsTransition.ENTER_DURATION_MS * kotlin.math.abs(target - start))
                .toLong().coerceAtLeast(1L)
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

    private fun applyPreviewState(value: Float) {
        // 系统进度只换算方向，不额外套时间曲线；与普通进出共用属性帧。
        applyOpenState(1f - value, ghostVisible = false)
    }

    private fun applySettled(value: Float) {
        applyOpenState(value, ghostVisible = false)
        featureProvider()?.visibility = if (value >= 1f) View.GONE else View.VISIBLE
        settingsProvider()?.visibility = if (value >= 1f) View.VISIBLE else View.GONE
    }

    private fun applyOpenState(value: Float, ghostVisible: Boolean) {
        progress = value
        featureProvider()?.let { view ->
            applyFrame(view, TransitionFrames.exit(
                kind = SettingsTransition.KIND,
                progress = value,
                scaleTo = SettingsTransition.CONTENT_SCALE,
            ))
        }
        // 真实设置与退场截图共享同一帧，切换承载者不改变类型、缩放或透明度。
        val presentedSettings = if (ghostVisible) ghost else settingsProvider()
        presentedSettings?.let { view ->
            applyFrame(view, TransitionFrames.enter(
                kind = SettingsTransition.KIND,
                progress = value,
                scaleFrom = SettingsTransition.OVERLAY_SCALE_FROM,
            ))
        }
    }

    private fun applyFrame(view: View, frame: TransitionFrame) {
        view.visibility = View.VISIBLE
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
        view.pivotX = view.width / 2f
        view.pivotY = view.height / 2f
    }

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
