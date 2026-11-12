package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec
import kotlin.math.abs

/** 内嵌面板的唯一展开进度；手势预览接管后可从当前帧继续或恢复。 */
internal class ThemePanelTransitionRunner(private val onFrame: (Float) -> Unit) {
    var progress = 0f
        private set
    private var animator: ValueAnimator? = null
    fun preview(value: Float) { stop(); emit(value) }
    fun play(target: Float, onEnd: () -> Unit = {}) {
        stop()
        val from = progress
        if (from == target || !ValueAnimator.areAnimatorsEnabled()) {
            emit(target); onEnd(); return
        }
        val next = ValueAnimator.ofFloat(from, target).apply {
            duration = (Spec.PANEL_DURATION_MS * abs(target - from)).toLong().coerceAtLeast(1L)
            interpolator = MotionInterpolators.of(MotionCurve.FastOutSlowIn)
            addUpdateListener { emit(it.animatedValue as Float) }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    if (animator !== animation) return
                    animator = null
                    emit(target)
                    onEnd()
                }
            })
        }
        animator = next
        next.start()
    }
    fun stop() {
        animator?.removeAllListeners()
        animator?.removeAllUpdateListeners()
        animator?.cancel()
        animator = null
    }
    private fun emit(value: Float) { progress = value.coerceIn(0f, 1f); onFrame(progress) }
}

/** 一条时间轴：先伸展并断开描边，再让菜单入场；返回手势反向采样。 */
internal object ThemePanelFrames {
    fun extension(progress: Float) = (progress / Spec.PANEL_EXTENSION_END).coerceIn(0f, 1f)
    fun menu(progress: Float) = ((progress - Spec.PANEL_MENU_START) /
        (1f - Spec.PANEL_MENU_START)).coerceIn(0f, 1f)
    fun gap(progress: Float) = ((progress - Spec.PANEL_GAP_START) /
        (Spec.PANEL_EXTENSION_END - Spec.PANEL_GAP_START)).coerceIn(0f, 1f)
}
