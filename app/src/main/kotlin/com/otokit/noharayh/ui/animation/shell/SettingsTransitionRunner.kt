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

    /** 当前呈现进度：0=功能态，1=设置态；关闭接续预览的最后一帧。 */
    val openProgress: Float get() = progress

    fun openImmediate() {
        token++
        cancelAnimator()
        clearGhost()
        progress = 1f
        featureProvider()?.let { view ->
            view.alpha = 0f
            view.scaleX = SettingsTransition.CONTENT_SCALE
            view.scaleY = SettingsTransition.CONTENT_SCALE
            view.visibility = View.GONE
        }
        settingsProvider()?.let { view ->
            view.visibility = View.VISIBLE
            view.alpha = 1f
            view.scaleX = 1f
            view.scaleY = 1f
        }
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
                    if (!observer.isAlive) return
                    observer.removeOnGlobalLayoutListener(this)
                    if (generation != token) return
                    if (overlayHost.height > 0) {
                        animateOpen()
                    } else {
                        applySettled(1f)
                        featureProvider()?.visibility = View.GONE
                    }
                }
            }
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
                    animator = null
                    applySettled(1f)
                }
            })
            start()
        }
    }

    fun endPreview(settingsOpen: Boolean) {
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
        progress = 0f
        featureProvider()?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1f
            it.scaleX = 1f
            it.scaleY = 1f
        }
        settingsProvider()?.let {
            it.visibility = View.GONE
            it.alpha = 0f
            it.scaleX = SettingsTransition.OVERLAY_SCALE_FROM
            it.scaleY = SettingsTransition.OVERLAY_SCALE_FROM
        }
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
        progress = 1f - value
        val feature = featureProvider()
        val settings = settingsProvider()
        feature?.let {
            it.visibility = View.VISIBLE
            it.alpha = value
            val scale = SettingsTransition.CONTENT_SCALE +
                (1f - SettingsTransition.CONTENT_SCALE) * value
            it.scaleX = scale
            it.scaleY = scale
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
        }
        settings?.let {
            it.visibility = View.VISIBLE
            it.alpha = 1f - value
            val scale = 1f + (SettingsTransition.OVERLAY_SCALE_FROM - 1f) * value
            it.scaleX = scale
            it.scaleY = scale
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
        }
    }

    private fun applySettled(value: Float) {
        progress = value
        if (value >= 1f) {
            featureProvider()?.let {
                it.visibility = View.GONE
                it.alpha = 0f
                it.scaleX = SettingsTransition.CONTENT_SCALE
                it.scaleY = SettingsTransition.CONTENT_SCALE
            }
            settingsProvider()?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
            }
        } else {
            featureProvider()?.let {
                it.visibility = View.VISIBLE
                it.alpha = 1f
                it.scaleX = 1f
                it.scaleY = 1f
            }
            settingsProvider()?.let {
                it.visibility = View.GONE
                it.alpha = 0f
                it.scaleX = SettingsTransition.OVERLAY_SCALE_FROM
                it.scaleY = SettingsTransition.OVERLAY_SCALE_FROM
            }
        }
    }

    private fun applyOpenState(value: Float, ghostVisible: Boolean) {
        progress = value
        val feature = featureProvider()
        val settings = settingsProvider()
        feature?.let {
            val frame = TransitionFrames.exit(
                kind = SettingsTransition.KIND,
                progress = value,
                scaleTo = SettingsTransition.CONTENT_SCALE,
            )
            it.visibility = View.VISIBLE
            it.alpha = frame.alpha
            it.scaleX = frame.scale
            it.scaleY = frame.scale
            it.pivotX = it.width / 2f
            it.pivotY = it.height / 2f
        }
        settings?.let {
            if (!ghostVisible) {
                val frame = TransitionFrames.enter(
                    kind = SettingsTransition.KIND,
                    progress = value,
                    scaleFrom = SettingsTransition.OVERLAY_SCALE_FROM,
                )
                it.visibility = View.VISIBLE
                it.alpha = frame.alpha
                it.scaleX = frame.scale
                it.scaleY = frame.scale
                it.pivotX = it.width / 2f
                it.pivotY = it.height / 2f
            }
        }
        ghost?.let {
            // ghost 从设置态(1)向透明退场：p=1 时完全可见，p=0 时放大淡出。
            it.alpha = value.coerceIn(0f, 1f)
            val scale = 1f + (1f - value) * (SettingsTransition.OVERLAY_SCALE_FROM - 1f)
            it.scaleX = scale
            it.scaleY = scale
        }
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
        animator?.cancel()
        animator = null
    }
}
