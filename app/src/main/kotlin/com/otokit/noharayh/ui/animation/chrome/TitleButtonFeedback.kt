package com.otokit.noharayh.ui.animation.chrome

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.animation.chrome.TitleButtonPressSpec as Spec

/** 只执行标题按钮的局部反馈；不引用 chrome 实现，不决定点击或导航。 */
internal class TitleButtonFeedback(private val owner: View, private val invalidate: () -> Unit) {
    private val rings = ArrayList<Ring>()
    private var held: Ring? = null
    private var pressAt = 0L
    private var gesture = 0L
    private var hapticGesture = -1L
    var peakScale = 1.5f

    private val releaseHaptic = Runnable {
        if (hapticGesture == gesture) {
            owner.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
                else HapticFeedbackConstants.CLOCK_TICK,
            )
        }
        hapticGesture = -1L
    }

    private fun now() = AnimationUtils.currentAnimationTimeMillis()

    private fun spring(value: FloatValueHolder, target: Float, damping: Float, stiffness: Float) =
        SpringAnimation(value).apply {
            setSpring(SpringForce(target).setDampingRatio(damping).setStiffness(stiffness))
            setMinimumVisibleChange(Spec.MIN_VISIBLE_CHANGE)
            addUpdateListener { _, _, _ -> invalidate() }
        }

    private inner class Ring(val bornAt: Long) {
        val expansion = FloatValueHolder(0f)
        val animation = spring(expansion, peakScale, Spec.EXPAND_DAMPING, Spec.EXPAND_STIFFNESS)
        var released = false
        val rest = Runnable {
            if (!released) {
                // 保留停止阈值；运行中只调参，不替换 SpringForce。
                animation.spring.setDampingRatio(Spec.REST_DAMPING)
                    .setStiffness(Spec.REST_STIFFNESS)
                animation.animateToFinalPosition(1f)
            }
        }
        val shrink = Runnable {
            if (released) {
                animation.spring.setDampingRatio(Spec.RELEASE_DAMPING)
                    .setStiffness(Spec.RELEASE_STIFFNESS)
                animation.animateToFinalPosition(0f)
            }
        }

        fun release() {
            if (released) return
            released = true
            owner.postDelayed(shrink, (Spec.EXPAND_DURATION_MS - (now() - bornAt)).coerceAtLeast(0L))
        }

        fun cancel() {
            owner.removeCallbacks(rest)
            owner.removeCallbacks(shrink)
            animation.cancel()
        }
    }

    fun press() {
        held?.release()
        rings.removeAll { it.released && !it.animation.isRunning && it.expansion.value <= 0.01f }
        while (rings.size >= Spec.MAX_RINGS) rings.removeAt(0).cancel()
        val ring = Ring(now())
        rings += ring
        held = ring
        ring.animation.animateToFinalPosition(peakScale)
        owner.postDelayed(ring.rest, Spec.EXPAND_DURATION_MS)
        gesture++
        owner.removeCallbacks(releaseHaptic)
        pressAt = now()
        owner.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    fun release(clicked: Boolean) {
        val ring = held ?: return
        held = null
        ring.release()
        if (clicked) {
            hapticGesture = gesture
            owner.postDelayed(releaseHaptic, (Spec.MIN_HAPTIC_GAP_MS - (now() - pressAt)).coerceAtLeast(0L))
        }
        invalidate()
    }

    fun forEachExpansion(draw: (Float) -> Unit) {
        rings.removeAll { it.released && !it.animation.isRunning && it.expansion.value <= 0.01f }
        rings.forEach { draw(it.expansion.value.coerceAtLeast(0f)) }
    }

    fun reset() {
        gesture++
        owner.removeCallbacks(releaseHaptic)
        hapticGesture = -1L
        rings.forEach { it.cancel() }
        rings.clear()
        held = null
        invalidate()
    }
}
