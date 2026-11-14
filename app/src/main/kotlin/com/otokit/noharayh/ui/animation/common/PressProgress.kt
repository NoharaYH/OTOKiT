package com.otokit.noharayh.ui.animation.common

import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.constants.motion.PressSurfaceSpec as Spec

/** 内缩、胶囊化和白色反馈共享的按压进度；调用者负责在卸载时 reset。 */
internal class PressProgress(onFrame: () -> Unit) {
    private val holder = FloatValueHolder(0f)
    val value: Float get() = holder.value
    private val spring = SpringAnimation(holder).apply {
        setSpring(SpringForce(0f).setDampingRatio(Spec.PRESS_DAMPING).setStiffness(Spec.PRESS_STIFFNESS))
        setMinimumVisibleChange(Spec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ -> onFrame() }
    }
    fun animateTo(value: Float) { spring.animateToFinalPosition(value) }
    fun reset() { spring.cancel(); holder.value = 0f }
}
