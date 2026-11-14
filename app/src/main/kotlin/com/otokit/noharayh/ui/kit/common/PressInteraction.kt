package com.otokit.noharayh.ui.kit.common

import android.os.SystemClock
import android.view.View
import com.otokit.noharayh.ui.constants.motion.PressClickSpec
import com.otokit.noharayh.ui.constants.motion.PressHapticSpec

import com.otokit.noharayh.ui.animation.common.PressFeedback
import com.otokit.noharayh.ui.animation.common.PressHaptics

/** 协调手势、触觉交付与点击时序；反馈执行器不负责业务点击。 */
internal class PressInteraction(private val host: View, private val feedback: PressFeedback = PressHaptics(host)) {
    /** 按下触觉的开始时间与手势序号；用于把抬起触觉排到按下播放结束之后。 */
    private var pressHapticAt = 0L
    private var gestureSeq = 0
    private var scheduledHapticSeq = -1
    var deferredClick = false
        private set
    private val releaseHapticRunnable = Runnable {
        val valid = scheduledHapticSeq == gestureSeq
        scheduledHapticSeq = -1
        if (valid) deliverReleaseFeedback()
    }
    private val deferredClickRunnable = Runnable {
        val dispatch = deferredClick && canDeliverFeedback()
        deferredClick = false
        if (dispatch) host.performClick()
    }

    private fun canDeliverFeedback(): Boolean =
        host.isAttachedToWindow && host.hasWindowFocus() && host.isShown && host.isEnabled && host.isClickable

    /** 点击已经验收：源 View 可因重绘、动画或站内导航被禁用/替换，窗口仍可交付抬手触觉。 */
    private fun requestAcceptedReleaseHaptic() {
        if (!host.isHapticFeedbackEnabled) return
        val window = host.rootView
        val sequence = gestureSeq
        window.postDelayed({
            if (sequence == gestureSeq && window.isAttachedToWindow && window.hasWindowFocus() && window.isShown) {
                feedback.release(window)
            }
        }, releaseHapticDelay())
    }

    private fun releaseHapticDelay(): Long =
        (PressHapticSpec.MIN_HAPTIC_GAP_MS - (SystemClock.uptimeMillis() - pressHapticAt)).coerceAtLeast(0L)

    private fun deliverReleaseFeedback() {
        if (!canDeliverFeedback()) {
            cancelDeferredClick()
            return
        }
        if (!deferredClick) {
            feedback.release(host)
            return
        }
        // 触觉关闭或系统拒绝时不额外等待；受理后留出短暂播放时间再离开窗口。
        val accepted = feedback.release(host)
        if (accepted) host.postDelayed(deferredClickRunnable, PressClickSpec.RELEASE_FEEDBACK_SETTLE_MS)
        else deferredClickRunnable.run()
    }

    fun cancelDeferredClick() {
        deferredClick = false
        scheduledHapticSeq = -1
        host.removeCallbacks(releaseHapticRunnable)
        host.removeCallbacks(deferredClickRunnable)
    }

    /**
     * 抬起触觉。
     *
     * 快速点按时，按下震动（本机 VIRTUAL_KEY 约 80–90ms）还在播放，OEM 的 HAL 会把紧接着的
     * 抬起震动标记为 `ignored_for_ongoing` 丢掉。这里按“距按下至少 120ms”补齐等待，
     * 并用手势序号丢弃过期回调，避免延迟到下一次点按才响。
     */
    private fun requestReleaseHaptic() {
        val delay = releaseHapticDelay()
        if (delay == 0L) {
            deliverReleaseFeedback()
            return
        }
        scheduledHapticSeq = gestureSeq
        host.removeCallbacks(releaseHapticRunnable)
        host.postDelayed(releaseHapticRunnable, delay)
    }

    fun press() {
        gestureSeq++
        host.removeCallbacks(releaseHapticRunnable)
        pressHapticAt = SystemClock.uptimeMillis()
        feedback.press()
    }

    fun click(defer: Boolean, releaseEnabled: Boolean) {
        if (defer && releaseEnabled) {
            deferredClick = true
            requestReleaseHaptic()
        } else {
            if (releaseEnabled) requestAcceptedReleaseHaptic()
            host.performClick()
        }
    }
}
