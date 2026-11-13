package com.otokit.noharayh.ui.animation.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec
import kotlin.math.abs
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

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

/** 一条时间轴：伸展到阈值后允许硬切描边，再让菜单入场；返回手势反向采样。 */
internal object ThemePanelFrames {
    fun extension(progress: Float) = (progress / Spec.PANEL_EXTENSION_END).coerceIn(0f, 1f)
    fun menu(progress: Float) = ((progress - Spec.PANEL_MENU_START) /
        (1f - Spec.PANEL_MENU_START)).coerceIn(0f, 1f)
    fun gap(progress: Float) = if (progress > Spec.PANEL_GAP_START) 1f else 0f
    // 先在外框尚未缩短的阶段完成滑块退场；预测返回取消时反向采样同一进度。
    fun scrollbarExit(start: Float, current: Float): Float =
        if (start <= 0f) 1f else ((start - current) / (start * Spec.SCROLLBAR_EXIT_FRACTION)).coerceIn(0f, 1f)
}

/** 一次冲量驱动完整拉伸回弹；保留速度和越过平衡点的挤压，不裁掉弹性尾段。 */
internal class ThemeScrollbarMotionRunner(private val onFrame: (Float, Float) -> Unit) {
    private val position = FloatValueHolder(0f)
    private var peak = 0f
    private var returning = false
    private var velocity = 0f
    private var pausedRunning = false
    private val spring = SpringAnimation(position).apply {
        spring = SpringForce(0f).setDampingRatio(Spec.SCROLLBAR_DAMPING)
            .setStiffness(Spec.SCROLLBAR_STIFFNESS)
        setMinimumVisibleChange(0.001f)
        addUpdateListener { _, value, speed ->
            velocity = speed
            peak = maxOf(peak, value)
            if (speed <= 0f && peak > 0f) returning = true
            // 负位移和大于 1 的压缩量正是回弹后的挤压，必须交给绘制层保留。
            onFrame(value, if (returning) 1f - value / peak else 0f)
        }
        addEndListener { _, canceled, _, _ -> if (!canceled) onFrame(0f, 1f) }
    }

    fun start() {
        reset()
        if (!ValueAnimator.areAnimatorsEnabled()) { onFrame(0f, 1f); return }
        spring.setStartValue(0f).setStartVelocity(Spec.SCROLLBAR_INITIAL_VELOCITY).start()
    }

    fun pause() { pausedRunning = spring.isRunning; spring.cancel() }
    fun resume() {
        if (pausedRunning) spring.setStartValue(position.value).setStartVelocity(velocity).start()
        pausedRunning = false
    }
    fun finish() { pausedRunning = false; spring.cancel(); onFrame(0f, 1f) }
    fun reset() {
        spring.cancel()
        peak = 0f
        returning = false
        velocity = 0f
        pausedRunning = false
        position.value = 0f
        onFrame(0f, 0f)
    }
}
