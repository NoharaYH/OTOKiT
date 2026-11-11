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

    /**
     * 设置父/子页标题与页面共用同一方向、距离和端点。
     *
     * 手机是整行横移；分栏沿用页面的同一套缩放/淡变。
     */
    fun title(
        progress: Float,
        entering: Boolean,
        forward: Boolean,
        split: Boolean,
        width: Float,
    ) = if (entering) TransitionFrames.enter(
        if (split) Spec.SPLIT_KIND else Spec.PHONE_KIND,
        progress,
        Spec.PAGE_SCALE,
        if (split) 0f else width * if (forward) 1f else -1f,
    ) else TransitionFrames.exit(
        if (split) Spec.SPLIT_KIND else Spec.PHONE_KIND,
        progress,
        Spec.PAGE_SCALE,
        if (split) 0f else width * if (forward) -1f else 1f,
    )
}
