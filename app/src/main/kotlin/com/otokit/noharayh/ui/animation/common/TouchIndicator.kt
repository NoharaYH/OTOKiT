package com.otokit.noharayh.ui.animation.common

import android.view.View
import android.view.animation.AnimationUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.constants.motion.PressSurfaceSpec

/** 触点尺寸弹簧与位置跟随；不处理点击或业务状态。 */
internal class TouchIndicator(
private val host: View, val bornAt: Long, startX: Float, startY: Float,
private val maximumScale: Float, private val expandMs: Long,
) {

    var x = startX
    var y = startY
    var targetX = startX
    var targetY = startY

    private var released = false

    val expansion = FloatValueHolder(0f)
    val spring = SpringAnimation(expansion).apply {
        setSpring(
            SpringForce(maximumScale).apply {
                dampingRatio = PressSurfaceSpec.EXPAND_DAMPING
                stiffness = PressSurfaceSpec.EXPAND_STIFFNESS
            },
        )
        setMinimumVisibleChange(PressSurfaceSpec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ -> host.invalidate() }
    }

    private val retractToRest = Runnable {
        if (released) return@Runnable
        // 回落稍慢、阻尼更高，避免在静止尺寸上反复弹动。
        // 运行中保留同一个 SpringForce，避免新实例丢失 start() 初始化的停止阈值。
        spring.spring.setDampingRatio(PressSurfaceSpec.REST_DAMPING)
            .setStiffness(PressSurfaceSpec.REST_STIFFNESS)
        spring.animateToFinalPosition(1f)
    }

    private val shrink = Runnable {
        if (!released) return@Runnable
        spring.spring.setDampingRatio(PressSurfaceSpec.RELEASE_DAMPING)
            .setStiffness(PressSurfaceSpec.RELEASE_STIFFNESS)
        spring.animateToFinalPosition(0f)
    }

    fun start() {
        spring.animateToFinalPosition(maximumScale)
        host.postDelayed(retractToRest, expandMs)
    }

    fun release() {
        if (released) return
        released = true
        val age = AnimationUtils.currentAnimationTimeMillis() - bornAt
        host.postDelayed(shrink, (expandMs - age).coerceAtLeast(0L))
    }

    fun cancel() {
        host.removeCallbacks(retractToRest)
        host.removeCallbacks(shrink)
        spring.cancel()
    }

    fun follow(deltaMs: Long, tauMs: Float) {
        val fraction = 1f - kotlin.math.exp(-deltaMs / tauMs.coerceAtLeast(0.01f))
        x += (targetX - x) * fraction
        y += (targetY - y) * fraction
    }

    fun isDead(): Boolean = released && !spring.isRunning && expansion.value <= 0.01f
}
