package com.otokit.noharayh.ui.animation.chrome

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.view.View
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.ChromeIconTransition
import com.otokit.noharayh.ui.constants.motion.ChromeThemeTransition

/** 图标与主题色过渡的执行、取消和收尾；仅接收资源 ID、颜色与绘制入口。 */
internal class TitleAppearanceTransitionRunner(
    private val iconLayer: ImageView,
    private val outgoingIconLayer: ImageView,
    private val applyCircleColor: (Int) -> Unit,
    private val applyDecorColors: (Int, Int) -> Unit,
) {
    private var disposed = false
    private var displayedIconId: Int? = null
    private var displayedCircleColor: Int? = null
    private var displayedMaiDecorColor: Int? = null
    private var displayedChuDecorColor: Int? = null
    private var iconAnimator: ValueAnimator? = null
    private var circleColorAnimator: ValueAnimator? = null
    private var decorAnimator: ValueAnimator? = null
    private var iconGeneration = 0
    private var circleColorGeneration = 0

    fun updateIcon(drawable: Int) {
        if (disposed) return
        val action = drawable
        if (displayedIconId == null) {
            displayedIconId = action
            iconLayer.setImageResource(action)
            applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
            outgoingIconLayer.visibility = View.INVISIBLE
            outgoingIconLayer.alpha = 0f
            return
        }
        if (displayedIconId == action) return
        val previous = displayedIconId ?: action
        displayedIconId = action
        if (!animationsEnabled()) {
            iconGeneration++
            iconAnimator?.cancel()
            iconAnimator = null
            iconLayer.setImageResource(action)
            applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
            outgoingIconLayer.visibility = View.INVISIBLE
            outgoingIconLayer.alpha = 0f
            return
        }

        iconGeneration++
        val generation = iconGeneration
        iconAnimator?.cancel()
        iconAnimator = null
        // 双层交叉淡变：旧图标保持可见并淡出，新图标直接叠在上面淡入，不出现空白帧。
        outgoingIconLayer.setImageResource(previous)
        outgoingIconLayer.visibility = View.VISIBLE
        outgoingIconLayer.alpha = 1f
        iconLayer.setImageResource(action)
        iconLayer.visibility = View.VISIBLE
        iconLayer.alpha = 0f

        iconAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeIconTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeIconTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                applyIconFrame(outgoingIconLayer, TransitionFrames.exit(
                    kind = ChromeIconTransition.KIND,
                    progress = p,
                    scaleTo = ChromeIconTransition.SCALE_FROM,
                    translationYTo = -iconLayer.height * ChromeIconTransition.TRANSLATION_SIZE_FRACTION,
                ))
                applyIconFrame(iconLayer, TransitionFrames.enter(
                    kind = ChromeIconTransition.KIND,
                    progress = p,
                    scaleFrom = ChromeIconTransition.SCALE_FROM,
                    translationYFrom = iconLayer.height * ChromeIconTransition.TRANSLATION_SIZE_FRACTION,
                ))
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (generation != iconGeneration) return
                    outgoingIconLayer.alpha = 0f
                    outgoingIconLayer.visibility = View.INVISIBLE
                    iconLayer.setImageResource(action)
                    applyIconFrame(iconLayer, TransitionFrame(1f, 1f, 0f, 0f))
                    if (iconAnimator === animation) iconAnimator = null
                }
            })
            start()
        }
    }
    private fun applyIconFrame(view: View, frame: TransitionFrame) {
        view.alpha = frame.alpha
        view.scaleX = frame.scale
        view.scaleY = frame.scale
        view.translationX = frame.translationX
        view.translationY = frame.translationY
    }
    fun updateCircleColor(target: Int, animate: Boolean) {
        if (disposed) return
        val current = displayedCircleColor
        displayedCircleColor = target
        if (current == null || current == target) {
            circleColorGeneration++
            circleColorAnimator?.cancel()
            circleColorAnimator = null
            applyCircleColor(target)
            return
        }
        if (!animate || !animationsEnabled()) {
            circleColorGeneration++
            circleColorAnimator?.cancel()
            circleColorAnimator = null
            applyCircleColor(target)
            return
        }
        circleColorGeneration++
        val generation = circleColorGeneration
        circleColorAnimator?.cancel()
        val evaluator = ArgbEvaluator()
        circleColorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeThemeTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeThemeTransition.CURVE)
            addUpdateListener { animation ->
                if (generation != circleColorGeneration) return@addUpdateListener
                applyCircleColor(evaluator.evaluate(
                    animation.animatedValue as Float,
                    current,
                    target,
                ) as Int)
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    if (generation != circleColorGeneration) return
                    applyCircleColor(target)
                    if (circleColorAnimator === animation) circleColorAnimator = null
                }
            })
            start()
        }
    }
    fun updateDecor(maiColor: Int, chuColor: Int, animate: Boolean = true) {
        if (disposed) return
        val previousMai = displayedMaiDecorColor
        val previousChu = displayedChuDecorColor
        displayedMaiDecorColor = maiColor
        displayedChuDecorColor = chuColor
        if (!animate || !animationsEnabled() || previousMai == null || previousChu == null) {
            decorAnimator?.cancel()
            decorAnimator = null
            applyDecorColors(maiColor, chuColor)
            return
        }
        if (previousMai == maiColor && previousChu == chuColor) return
        decorAnimator?.cancel()
        val evaluator = ArgbEvaluator()
        decorAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = ChromeThemeTransition.DURATION_MS
            interpolator = MotionInterpolators.of(ChromeThemeTransition.CURVE)
            addUpdateListener { animation ->
                val p = animation.animatedValue as Float
                applyDecorColors(
                    evaluator.evaluate(p, previousMai, maiColor) as Int,
                    evaluator.evaluate(p, previousChu, chuColor) as Int,
                )
            }
            start()
        }
    }

    /** 宿主失去前台或几何失效时，直接呈现最后一次收到的目标。 */
    fun settle() {
        if (disposed) return
        iconAnimator?.end()
        circleColorAnimator?.end()
        decorAnimator?.end()
        iconAnimator = null
        circleColorAnimator = null
        decorAnimator = null
    }

    fun dispose() {
        if (disposed) return
        disposed = true
        iconGeneration++
        circleColorGeneration++
        val icon = iconAnimator
        val circle = circleColorAnimator
        val decor = decorAnimator
        iconAnimator = null
        circleColorAnimator = null
        decorAnimator = null
        icon?.cancel()
        circle?.cancel()
        decor?.cancel()
    }

    private fun animationsEnabled(): Boolean = ValueAnimator.areAnimatorsEnabled()
}
