package com.otokit.noharayh.ui.chrome

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.animation.AnimationUtils
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.otokit.noharayh.ui.kit.common.NavCapsuleView
import kotlin.math.max

/**
 * 抽屉胶囊的黑色外环与内收驱动。
 *
 * 复用汉堡键的“内收 + 外环”状态机，但参数与实例独立（见 [NavCapsulePressSpec]）。
 * 一次只服务一个胶囊：按下新胶囊会先收起旧外环。
 */
internal class NavCapsuleHaloView(context: Context) : View(context) {

    private val density = resources.displayMetrics.density
    private val black = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(NavCapsulePressSpec.HALO_ALPHA, 0, 0, 0)
    }
    private val rect = RectF()
    private val ownLocation = IntArray(2)
    private val targetLocation = IntArray(2)

    private val restOutset = NavCapsulePressSpec.HALO_REST_OUTSET_DP * density
    private val peakOutset = NavCapsulePressSpec.HALO_PEAK_OUTSET_DP * density
    private val peakScale = peakOutset / restOutset

    private val expansion = FloatValueHolder(0f)
    private val expansionAnim = spring(expansion, 0f, NavCapsulePressSpec.EXPAND_DAMPING, NavCapsulePressSpec.EXPAND_STIFFNESS)

    private val press = FloatValueHolder(0f)
    private val pressAnim = spring(press, 0f, NavCapsulePressSpec.PRESS_DAMPING, NavCapsulePressSpec.PRESS_STIFFNESS)

    private var target: NavCapsuleView? = null
    private var released = true
    private var pressAt = 0L
    private var gesture = 0L
    private var hapticGesture = -1L

    private val restRunnable = Runnable {
        if (!released) {
            expansionAnim.spring = SpringForce(1f)
                .setDampingRatio(NavCapsulePressSpec.REST_DAMPING)
                .setStiffness(NavCapsulePressSpec.REST_STIFFNESS)
            expansionAnim.animateToFinalPosition(1f)
        }
    }

    private val shrinkRunnable = Runnable {
        if (released) {
            expansionAnim.spring = SpringForce(0f)
                .setDampingRatio(NavCapsulePressSpec.RELEASE_DAMPING)
                .setStiffness(NavCapsulePressSpec.RELEASE_STIFFNESS)
            expansionAnim.animateToFinalPosition(0f)
        }
    }

    private val releaseHaptic = Runnable {
        if (hapticGesture == gesture) {
            performHapticFeedback(
                if (android.os.Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
                else HapticFeedbackConstants.CLOCK_TICK,
            )
        }
        hapticGesture = -1L
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable = false
    }

    private fun spring(
        value: FloatValueHolder,
        target: Float,
        damping: Float,
        stiffness: Float,
    ) = SpringAnimation(value).apply {
        setSpring(SpringForce(target).setDampingRatio(damping).setStiffness(stiffness))
        setMinimumVisibleChange(NavCapsulePressSpec.MIN_VISIBLE_CHANGE)
        addUpdateListener { _, _, _ ->
            invalidate()
            this@NavCapsuleHaloView.target?.setPressProgress(press.value.coerceIn(0f, 1f))
        }
    }

    fun press(host: NavCapsuleView) {
        if (target !== host) {
            target?.let { old ->
                old.setPressProgress(0f)
            }
        }
        removeCallbacks(restRunnable)
        removeCallbacks(shrinkRunnable)
        target = host
        released = false
        target?.setPressProgress(0f)
        expansionAnim.animateToFinalPosition(peakScale)
        postDelayed(restRunnable, NavCapsulePressSpec.EXPAND_DURATION_MS)
        pressAnim.animateToFinalPosition(1f)
        gesture++
        pressAt = AnimationUtils.currentAnimationTimeMillis()
        removeCallbacks(releaseHaptic)
        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        invalidate()
    }

    fun release(host: NavCapsuleView, clicked: Boolean) {
        if (target !== host) return
        released = true
        pressAnim.animateToFinalPosition(0f)
        val elapsed = AnimationUtils.currentAnimationTimeMillis() - pressAt
        postDelayed(
            shrinkRunnable,
            (NavCapsulePressSpec.EXPAND_DURATION_MS - elapsed).coerceAtLeast(0L),
        )
        if (clicked) {
            hapticGesture = gesture
            postDelayed(
                releaseHaptic,
                (NavCapsulePressSpec.MIN_HAPTIC_GAP_MS - elapsed).coerceAtLeast(0L),
            )
        }
        invalidate()
    }

    /** 清空目标并复位，用于抽屉关闭、内容移除或窗口变化。 */
    fun reset() {
        removeCallbacks(restRunnable)
        removeCallbacks(shrinkRunnable)
        removeCallbacks(releaseHaptic)
        target?.setPressProgress(0f)
        target = null
        released = true
        expansionAnim.cancel()
        pressAnim.cancel()
        expansion.value = 0f
        press.value = 0f
        hapticGesture = -1L
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val host = target ?: return
        if (expansion.value <= 0.001f || host.width == 0 || host.height == 0) return

        getLocationInWindow(ownLocation)
        host.getLocationInWindow(targetLocation)
        val left = (targetLocation[0] - ownLocation[0]).toFloat()
        val top = (targetLocation[1] - ownLocation[1]).toFloat()
        val outset = restOutset * expansion.value

        rect.set(left - outset, top - outset, left + host.width + outset, top + host.height + outset)
        val radius = max(rect.height() / 2f, 0f)
        canvas.drawRoundRect(rect, radius, radius, black)
    }
}
