package com.otokit.noharayh.ui.shell.background

import android.content.Context
import android.graphics.Color
import android.view.View
import com.otokit.noharayh.ui.constants.motion.BackgroundVeilTransition

/**
 * 背景首帧灰色遮罩。
 *
 * 只覆盖背景宿主，不处理触摸、焦点或无障碍；淡出由 animation/shell 执行。
 */
internal class BackgroundVeilView(context: Context) : View(context) {

    init {
        setBackgroundColor(Color.rgb(
            Color.red(BackgroundVeilTransition.COLOR_ARGB),
            Color.green(BackgroundVeilTransition.COLOR_ARGB),
            Color.blue(BackgroundVeilTransition.COLOR_ARGB),
        ))
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }
}
