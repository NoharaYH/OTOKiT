package com.otokit.noharayh.ui.animation.shell

import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.constants.motion.TransitionKind

/** 设置内容的纯空间帧；普通播放、预览、回弹与截图均调用这里。 */
object SettingsTransitionFrames {
    fun feature(progress: Float, height: Float): TransitionFrame = TransitionFrames.exit(
        kind = SettingsTransition.KIND,
        progress = progress,
        scaleTo = SettingsTransition.CONTENT_SCALE,
        translationYTo = -height * if (SettingsTransition.KIND == TransitionKind.Slide) {
            SettingsTransition.SLIDE_CONTENT_TRANSLATION_FRACTION
        } else {
            SettingsTransition.COMBINED_CONTENT_TRANSLATION_FRACTION
        },
    )

    fun overlay(progress: Float, height: Float): TransitionFrame = TransitionFrames.enter(
        kind = SettingsTransition.KIND,
        progress = progress,
        scaleFrom = SettingsTransition.OVERLAY_SCALE_FROM,
        translationYFrom = height * if (SettingsTransition.KIND == TransitionKind.Slide) {
            SettingsTransition.SLIDE_OVERLAY_TRANSLATION_FRACTION
        } else {
            SettingsTransition.COMBINED_OVERLAY_TRANSLATION_FRACTION
        },
    )
}
