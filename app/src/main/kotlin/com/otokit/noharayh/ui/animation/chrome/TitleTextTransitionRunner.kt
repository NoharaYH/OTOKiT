package com.otokit.noharayh.ui.animation.chrome

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTitleTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.theme.GameLine

/** 标题文字转场的唯一执行者，拥有 Animator 与 ghost；不读取或提交导航状态。 */
internal class TitleTextTransitionRunner(
    private val titleHost: FrameLayout,
    private val titleMotionHost: View,
    private val functionalHost: View,
    private val settingsColumn: View,
    private val maiStack: FrameLayout,
    private val chuStack: FrameLayout,
    private val refreshPivots: () -> Unit,
    private val refreshStackPivot: (FrameLayout) -> Unit,
) {
    private var disposed = false
    private var navAnimator: ValueAnimator? = null
    private var settingsAnimator: ValueAnimator? = null
    private var ghost: ImageView? = null
    private var settingsProgress = 0f
    var backPreviewActive = false
        private set
    val settingsAnimating: Boolean get() = settingsAnimator != null

    /** 预测返回：0=完全设置态，1=完全功能态；调用方只给进度，不提交任何状态。 */
    fun beginBackPreview() {
        if (disposed) return
        cancelNavigation()
        cancelSettingsTransition()
        backPreviewActive = true
        // 预览进度 0=设置态；内部设置进度 1=设置态。
        applySettingsProgress(1f)
    }

    fun updateBackProgress(progress: Float) {
        if (disposed || !backPreviewActive) return
        applySettingsProgress(1f - progress.coerceIn(0f, 1f))
    }

    /** 取消预览：回到当前稳定设置态；不调用 closeSettings。 */
    fun cancelBackPreview(settingsOpen: Boolean) {
        if (disposed || !backPreviewActive) return
        backPreviewActive = false
        animateSettingsTo(if (settingsOpen) 1f else 0f)
    }

    fun endBackPreview() {
        backPreviewActive = false
        // 保留预览最后一帧，已提交状态的退出动画从这里继续。
    }

    fun applyGameFrame(frame: GameFrame?, currentGame: GameLine) {
        if (disposed) return
        val progress = frame?.normalizedProgress
            ?: if (currentGame == GameLine.Mai) 0f else 1f
        val phase = frame?.phase ?: GamePhase.Idle
        val selectedMai = currentGame == GameLine.Mai || progress < 0.5f

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

    fun settleSettings(settingsOpen: Boolean) {
        if (disposed) return
        cancelSettingsTransition()
        if (settingsOpen) {
            applySettingsProgress(1f)
        } else {
            applySettingsProgress(0f)
        }
    }

    private fun applySettingsProgress(progress: Float) {
        val p = progress.coerceIn(0f, 1f)
        settingsProgress = p
        refreshPivots()
        val exit = TransitionFrames.exit(
            kind = SettingsTitleTransition.KIND,
            progress = p,
            scaleTo = SettingsTitleTransition.SCALE_SHRINK,
        )
        functionalHost.visibility = if (p >= 1f) View.INVISIBLE else View.VISIBLE
        functionalHost.alpha = exit.alpha
        functionalHost.scaleX = exit.scale
        functionalHost.scaleY = exit.scale

        val enter = TransitionFrames.enter(
            kind = SettingsTitleTransition.KIND,
            progress = p,
            scaleFrom = SettingsTitleTransition.SCALE_EXPAND_FROM,
        )
        settingsColumn.visibility = if (p <= 0f) View.INVISIBLE else View.VISIBLE
        settingsColumn.alpha = enter.alpha
        settingsColumn.scaleX = enter.scale
        settingsColumn.scaleY = enter.scale
    }

    fun animateSettings(open: Boolean): Boolean {
        if (disposed || !animationsEnabled() || titleHost.width <= 0) return false
        cancelSettingsTransition()
        val from = settingsProgress
        val to = if (open) 1f else 0f
        settingsAnimator = ValueAnimator.ofFloat(from, to).apply {
            val fullDuration = if (open) SettingsTransition.ENTER_DURATION_MS else SettingsTransition.EXIT_DURATION_MS
            duration = (fullDuration * kotlin.math.abs(to - from)).toLong().coerceAtLeast(1L)
            interpolator = MotionInterpolators.of(SettingsTitleTransition.CURVE)
            addUpdateListener { animation ->
                applySettingsProgress(animation.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (settingsAnimator === animation) {
                        settingsAnimator = null
                        settleSettings(open)
                    }
                }
            })
            start()
        }
        return true
    }

    private fun animateSettingsTo(target: Float) {
        cancelSettingsTransition()
        if (!animationsEnabled()) {
            applySettingsProgress(target)
            return
        }
        settingsAnimator = ValueAnimator.ofFloat(settingsProgress, target).apply {
            duration = if (target >= 1f) SettingsTransition.ENTER_DURATION_MS else SettingsTransition.EXIT_DURATION_MS
            interpolator = MotionInterpolators.of(SettingsTitleTransition.CURVE)
            addUpdateListener { animation ->
                applySettingsProgress(animation.animatedValue as Float)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (settingsAnimator === animation) {
                        settingsAnimator = null
                        applySettingsProgress(target)
                    }
                }
            })
            start()
        }
    }

    private fun cancelSettingsTransition() {
        val previous = settingsAnimator
        settingsAnimator = null
        previous?.cancel()
    }

    fun runNavigation(translationY: Float, updateTitles: () -> Unit) {
        if (disposed) return
        cancelNavigation()
        val ghostView = captureGhost()
        val dy = translationY
        updateTitles()

        if (!animationsEnabled() || ghostView == null) {
            titleMotionHost.translationY = 0f
            titleMotionHost.alpha = 1f
            clearGhost()
            return
        }
        titleMotionHost.alpha = 0f
        titleMotionHost.translationY = dy
        navAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = NavTitleTransition.DURATION_MS
            interpolator = MotionInterpolators.of(NavTitleTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                val entry = TransitionFrames.enter(
                    kind = NavTitleTransition.KIND,
                    progress = p,
                    translationYFrom = dy,
                )
                val exit = TransitionFrames.exit(
                    kind = NavTitleTransition.KIND,
                    progress = p,
                    translationYTo = -dy,
                )
                titleMotionHost.alpha = entry.alpha
                titleMotionHost.translationY = entry.translationY
                titleMotionHost.scaleX = entry.scale
                titleMotionHost.scaleY = entry.scale
                ghostView.alpha = exit.alpha
                ghostView.translationY = exit.translationY
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (navAnimator === animation) {
                        navAnimator = null
                        titleMotionHost.alpha = 1f
                        titleMotionHost.translationY = 0f
                        clearGhost()
                    }
                }
            })
            start()
        }
    }

    fun cancelNavigation() {
        val previous = navAnimator
        navAnimator = null
        previous?.cancel()
        titleMotionHost.alpha = 1f
        titleMotionHost.translationY = 0f
        clearGhost()
    }

    private fun captureGhost(): ImageView? {
        clearGhost()
        val width = titleHost.width
        val height = titleHost.height
        if (width <= 0 || height <= 0) return null
        val bitmap = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            return null
        }
        try {
            titleHost.draw(Canvas(bitmap))
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
            pivotX = 0f
            pivotY = height / 2f
        }
        titleHost.addView(
            image,
            FrameLayout.LayoutParams(width, height),
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
        cancelSettingsTransition()
    }

    private fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
}
