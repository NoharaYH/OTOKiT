package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition as Spec

/** 无状态映射；自动播放、真实手势与取消回弹共同消费。 */
object SettingsPageFrames {
    /** 用父页=0、子页=1 描述同一空间；返回和取消都反向采样，避免两套端点。 */
    fun unfoldPage(progress: Float, entering: Boolean, forward: Boolean, density: Float): TransitionFrame {
        val p = if (forward) progress else 1f - progress
        return if (entering == forward) TransitionFrame(
            alpha = window(p, Spec.UNFOLD_CONTENT_START, Spec.UNFOLD_CONTENT_END),
            scale = 1f,
            translationX = Spec.UNFOLD_PAGE_DISTANCE_DP * density * (1f - p.coerceIn(0f, 1f)),
            translationY = 0f,
        ) else TransitionFrame(
            alpha = 1f - window(p, 0f, Spec.UNFOLD_ROOT_END),
            scale = 1f,
            translationX = -Spec.UNFOLD_ROOT_DISTANCE_DP * density * window(p, 0f, Spec.UNFOLD_ROOT_END),
            translationY = 0f,
        )
    }

    fun unfoldReveal(progress: Float, forward: Boolean): Float =
        window(if (forward) progress else 1f - progress, 0f, Spec.UNFOLD_REVEAL_END)

    fun unfoldTitle(progress: Float, entering: Boolean, forward: Boolean, density: Float): TransitionFrame {
        val p = (if (forward) progress else 1f - progress).coerceIn(0f, 1f)
        return if (entering == forward) TransitionFrame(
            alpha = window(p, Spec.UNFOLD_TITLE_START, Spec.UNFOLD_TITLE_END),
            scale = 1f,
            translationX = Spec.UNFOLD_TITLE_DISTANCE_DP * density * (1f - p),
            translationY = 0f,
        ) else TransitionFrame(
            alpha = 1f - window(p, 0f, Spec.UNFOLD_TITLE_EXIT_END),
            scale = 1f,
            translationX = -Spec.UNFOLD_TITLE_DISTANCE_DP * density * p,
            translationY = 0f,
        )
    }

    private fun window(progress: Float, start: Float, end: Float) =
        ((progress - start) / (end - start)).coerceIn(0f, 1f)

    fun selectionChange(progress: Float): Float =
        (progress / Spec.SELECTION_WINDOW).coerceIn(0f, 1f)

    /** 手机进入先反色再展开；退出时页面与反色并行，共享完整进度。 */
    fun phoneUnfoldProgress(progress: Float, forward: Boolean): Float {
        return if (forward) window(progress, Spec.SELECTION_WINDOW, 1f)
            else progress.coerceIn(0f, 1f)
    }

    fun phoneSelectionChange(progress: Float, forward: Boolean): Float =
        if (forward) selectionChange(progress) else progress.coerceIn(0f, 1f)

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
