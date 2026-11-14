package com.otokit.noharayh.ui.kit.settings

import android.media.AudioAttributes
import android.os.Build
import android.os.SystemClock
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import com.otokit.noharayh.ui.constants.motion.ColorPickerMotion as Motion
import com.otokit.noharayh.ui.constants.motion.PressSurfaceSpec

/** 滑动用低能量短齿感；按下、松手仍与项目按钮一致，互相留出播放间隔。 */
internal class SliderHaptics(private val view: View) {
    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= 31) {
            view.context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            view.context.getSystemService(Vibrator::class.java)
        }
    }
    private val tickEffect by lazy { createTick() }
    private val legacyAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
    private var pressedAt = 0L
    private var lastTickAt = 0L
    private val releaseFeedback = Runnable {
        if (canPlay()) view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
            else HapticFeedbackConstants.CLOCK_TICK,
        )
    }

    fun press() {
        cancel()
        pressedAt = SystemClock.uptimeMillis()
        lastTickAt = 0L
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    fun tick() {
        val now = SystemClock.uptimeMillis()
        if (!canPlay() || now - pressedAt < PressSurfaceSpec.MIN_HAPTIC_GAP_MS) return
        val effect = tickEffect ?: return
        val motor = vibrator ?: return
        if (Build.VERSION.SDK_INT >= 33) {
            motor.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_TOUCH))
        } else {
            @Suppress("DEPRECATION")
            motor.vibrate(effect, legacyAttributes)
        }
        lastTickAt = now
    }

    fun release() {
        view.removeCallbacks(releaseFeedback)
        val readyAt = maxOf(pressedAt + PressSurfaceSpec.MIN_HAPTIC_GAP_MS,
            lastTickAt + Motion.DRAG_TICK_INTERVAL_MS)
        view.postDelayed(releaseFeedback, (readyAt - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    fun cancel() { view.removeCallbacks(releaseFeedback) }

    @Suppress("DEPRECATION") // 兼容旧系统的全局触觉开关；新系统同时通过 USAGE_TOUCH 分类。
    private fun canPlay(): Boolean = view.isAttachedToWindow && view.hasWindowFocus() &&
        view.isShown && view.isEnabled && view.isHapticFeedbackEnabled &&
        Settings.System.getInt(view.context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0

    private fun createTick(): VibrationEffect? {
        val motor = vibrator?.takeIf { it.hasVibrator() } ?: return null
        if (Build.VERSION.SDK_INT >= 31 && motor.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)) {
            return VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, Motion.DRAG_PRIMITIVE_SCALE).compose()
        }
        if (Build.VERSION.SDK_INT >= 30 && motor.areAllPrimitivesSupported(VibrationEffect.Composition.PRIMITIVE_TICK)) {
            return VibrationEffect.startComposition()
                .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, Motion.DRAG_PRIMITIVE_SCALE).compose()
        }
        // 部分 ROM 的普通 TICK 回退为 10ms 全幅脉冲，TEXTURE_TICK 又不支持。
        // 不依赖该回退；无幅度控制时只发 2ms，不堆成长震。
        return if (motor.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(Motion.DRAG_SOFT_PULSE_MS, Motion.DRAG_SOFT_AMPLITUDE)
        } else {
            VibrationEffect.createOneShot(Motion.DRAG_FALLBACK_PULSE_MS, VibrationEffect.DEFAULT_AMPLITUDE)
        }
    }
}
