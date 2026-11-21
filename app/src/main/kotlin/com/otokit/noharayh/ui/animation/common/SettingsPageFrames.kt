package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.SettingsPageTransition as Spec
import kotlin.math.roundToInt

/** 无状态映射；自动播放、真实手势与取消回弹共同消费。 */
object SettingsPageFrames {
    internal fun resistedBackProgress(progress: Float): Float = resistedDragDistance(
        progress.coerceIn(0f, 1f), Spec.BACK_DRAG_RESISTANCE_START, Spec.BACK_DRAG_RESISTANCE_OVERSHOOT,
    )

    internal data class SplitPanes(
        val rootWidth: Int,
        val secondaryLeft: Int,
        val secondaryWidth: Int,
        val archiveLeft: Int,
        val archiveWidth: Int,
    )

    internal fun splitPanes(width: Int, gap: Int, archiveProgress: Float): SplitPanes {
        val column = ((width - 2 * gap) / 3).coerceAtLeast(0)
        val secondaryLeft = column + gap
        val archiveWidth = (width - 2 * secondaryLeft).coerceAtLeast(0)
        val shown = (archiveWidth * archiveProgress).roundToInt()
        val openingGap = (gap * archiveProgress).roundToInt()
        return SplitPanes(column, secondaryLeft, (width - secondaryLeft - shown - openingGap).coerceAtLeast(0),
            width - shown, archiveWidth)
    }

    /** A section switch presents each section at its own settled width. */
    internal fun splitArchiveProgress(
        progress: Float,
        fromArchive: Boolean,
        toArchive: Boolean,
        switchesSection: Boolean,
        entering: Boolean,
    ): Float {
        val from = if (fromArchive) 1f else 0f
        val to = if (toArchive) 1f else 0f
        return if (switchesSection) {
            if (entering) to else from
        } else from + (to - from) * progress
    }

    /** 用父页=0、子页=1 描述同一空间；返回和取消都反向采样，避免两套端点。 */
    fun unfoldPage(progress: Float, entering: Boolean, forward: Boolean, density: Float): TransitionFrame {
        val p = if (forward) progress else 1f - progress
        val distance = Spec.UNFOLD_PAGE_DISTANCE_DP * density
        return if (entering == forward) PageTurnFrames.enter(p, dx = distance)
            else PageTurnFrames.exit(p, dx = -distance)
    }

    fun unfoldTitle(progress: Float, entering: Boolean, forward: Boolean, density: Float): TransitionFrame {
        val p = if (forward) progress else 1f - progress
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
