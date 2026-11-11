package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition as Spec

/** 无状态映射；自动播放、真实手势与取消回弹共同消费。 */
object SettingsPageFrames {
    fun selectionChange(progress: Float): Float =
        (progress / Spec.SELECTION_WINDOW).coerceIn(0f, 1f)

    fun page(progress: Float, entering: Boolean, forward: Boolean, split: Boolean, width: Float) =
        if (entering) TransitionFrames.enter(
            if (split) Spec.SPLIT_KIND else Spec.PHONE_KIND, progress, Spec.PAGE_SCALE,
            if (split) 0f else width * if (forward) 1f else -1f,
        ) else TransitionFrames.exit(
            if (split) Spec.SPLIT_KIND else Spec.PHONE_KIND, progress, Spec.PAGE_SCALE,
            if (split) 0f else width * if (forward) -1f else 1f,
        )

    fun title(progress: Float, entering: Boolean, forward: Boolean, height: Float) =
        if (entering) TransitionFrames.enter(
            Spec.TITLE_KIND, progress, Spec.TITLE_SCALE,
            translationYFrom = height * Spec.TITLE_DISTANCE_HEIGHT * if (forward) 1f else -1f,
        ) else TransitionFrames.exit(
            Spec.TITLE_KIND, progress, Spec.TITLE_SCALE,
            translationYTo = height * Spec.TITLE_DISTANCE_HEIGHT * if (forward) -1f else 1f,
        )
}
