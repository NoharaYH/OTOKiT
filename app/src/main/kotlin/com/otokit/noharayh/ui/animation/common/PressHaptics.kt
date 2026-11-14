package com.otokit.noharayh.ui.animation.common

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

/** 只执行反馈并返回系统是否受理；不安排点击、不保存手势或延迟任务。 */
internal interface PressFeedback {
    fun press()
    fun release(target: View): Boolean
}

internal class PressHaptics(private val host: View) : PressFeedback {
    override fun press() { host.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    override fun release(target: View): Boolean = target.performHapticFeedback(
        if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.GESTURE_END
        else HapticFeedbackConstants.CLOCK_TICK,
    )
}
