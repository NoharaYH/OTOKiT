package com.otokit.noharayh.ui.animation.settings

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.FlingAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion as Spec
import kotlin.math.abs

/** 惯性和吸附共享位置、互斥执行；不处理触摸、选择提交或触觉。 */
internal class ThemeMenuMotionRunner(
    density: Float,
    private val stride: Float,
    private val maximum: Float,
    private val snapPosition: (Float) -> Float,
    private val onPosition: (Float) -> Unit,
    private val onSettled: () -> Unit,
) {
    private enum class Phase { Idle, Fling, Snap }
    private var phase = Phase.Idle
    val isRunning: Boolean get() = phase != Phase.Idle
    private val position = FloatValueHolder(0f)
    private val spring = SpringAnimation(position).apply {
        spring = SpringForce(0f).setDampingRatio(Spec.SPRING_DAMPING)
            .setStiffness(Spec.SPRING_STIFFNESS)
        setMinimumVisibleChange(Spec.MIN_VISIBLE_CHANGE_DP * density)
        addUpdateListener { _, value, _ -> onPosition(value) }
        addEndListener { _, canceled, value, _ ->
            if (!canceled && phase == Phase.Snap) {
                onPosition(snapPosition(value))
                phase = Phase.Idle
                onSettled()
            }
        }
    }
    private val fling = FlingAnimation(position).apply {
        friction = Spec.FLING_FRICTION
        setMinValue(0f)
        setMaxValue(maximum)
        addUpdateListener { _, value, velocity ->
            onPosition(value)
            val remaining = if (velocity > 0) maximum - value else value
            if (abs(velocity) < stride * Spec.FLING_HANDOFF_SPEED ||
                remaining < stride * Spec.FLING_HANDOFF_DISTANCE) {
                snap(value, velocity, snapPosition(value + velocity / Spec.FLING_DECAY))
            }
        }
        addEndListener { _, canceled, value, velocity ->
            if (!canceled && phase == Phase.Fling) snap(value, velocity)
        }
    }

    fun startFling(from: Float, velocity: Float) {
        stop()
        phase = Phase.Fling
        fling.setStartValue(from).setStartVelocity(velocity).start()
        onPosition(from)
    }

    fun snap(from: Float, velocity: Float = 0f, target: Float = snapPosition(from)) {
        stop()
        phase = Phase.Snap
        spring.setStartValue(from)
        spring.setStartVelocity(velocity.coerceIn(-stride * Spec.SNAP_MAX_SPEED, stride * Spec.SNAP_MAX_SPEED))
        spring.animateToFinalPosition(target)
        onPosition(from)
    }

    fun stop() {
        phase = Phase.Idle
        fling.cancel()
        spring.cancel()
    }
}
