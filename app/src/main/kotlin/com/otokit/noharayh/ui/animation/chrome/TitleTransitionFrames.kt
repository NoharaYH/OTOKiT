package com.otokit.noharayh.ui.animation.chrome

import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
import com.otokit.noharayh.ui.constants.motion.NavigationTransition
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTitleTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.constants.motion.TransitionKind

/**
 * 标题文字转场的纯数学。
 *
 * 静止态使用 LM 锚点；转场由执行器补偿到进出侧边缘，避免缩放反拉。
 * 游戏横切、导航入场/ghost 退场共用内容的曲线，不创建 View/Animator。
 */
object TitleTransitionFrames {

    fun lmPivotY(inkTop: Float, inkBottom: Float): Float =
        (inkTop + inkBottom) / 2f

    fun gamePair(progress: Float, width: Float): Pair<TransitionFrame, TransitionFrame> =
        TransitionFrames.pagerPair(
            kind = PagerPairTransition.KIND,
            progress = progress,
            slideDistancePx = width * PagerPairTransition.CONTENT_TRANSLATION_RATIO,
            minScale = PagerPairTransition.MIN_SCALE,
            minAlpha = PagerPairTransition.MIN_ALPHA,
            cardMotion = true,
        )

    fun navEntry(progress: Float, translationY: Float): TransitionFrame = navBetween(
        start = TransitionFrames.enter(
            kind = NavigationTransition.KIND,
            progress = 0f,
            scaleFrom = navigationScale(),
            translationYFrom = translationY,
        ),
        end = TransitionFrames.enter(
            kind = NavigationTransition.KIND,
            progress = 1f,
            scaleFrom = navigationScale(),
            translationYFrom = translationY,
        ),
        progress = progress,
        entering = true,
    )

    fun navGhostExit(
        progress: Float,
        translationY: Float,
        from: TransitionFrame = TransitionFrame(1f, 1f, 0f, 0f),
    ): TransitionFrame {
        val target = TransitionFrames.exit(
            kind = NavigationTransition.KIND,
            progress = 1f,
            scaleTo = navigationScale(),
            translationYTo = translationY,
        )
        return navBetween(from, target, progress, entering = false)
    }

    /** 内容切页仍是唯一时钟；标题按自己的距离采样同一组位移、淡变曲线。 */
    private fun navBetween(start: TransitionFrame, end: TransitionFrame, progress: Float, entering: Boolean): TransitionFrame {
        val travel = TransitionFrames.pageTravelProgress(progress, entering)
        val fade = TransitionFrames.pageFadeProgress(progress, entering)
        return TransitionFrame(
            alpha = TransitionFrames.lerp(start.alpha, end.alpha, fade),
            scale = TransitionFrames.pageScale(start.scale, end.scale, progress, entering),
            translationX = TransitionFrames.lerp(start.translationX, end.translationX, travel),
            translationY = TransitionFrames.lerp(start.translationY, end.translationY, travel),
        )
    }

    fun settingsFeature(progress: Float, height: Float): TransitionFrame = TransitionFrames.exit(
        kind = SettingsTransition.KIND,
        progress = progress,
        scaleTo = SettingsTitleTransition.SCALE_SHRINK,
        translationYTo = -height * settingsTranslationFraction(),
    )

    fun settingsOverlay(progress: Float, height: Float): TransitionFrame = TransitionFrames.enter(
        kind = SettingsTransition.KIND,
        progress = progress,
        scaleFrom = SettingsTitleTransition.SCALE_EXPAND_FROM,
        translationYFrom = height * settingsTranslationFraction(),
    )

    private fun settingsTranslationFraction(): Float =
        if (SettingsTransition.KIND == TransitionKind.Slide) SettingsTitleTransition.SLIDE_TRANSLATION_HEIGHT_FRACTION
        else SettingsTitleTransition.COMBINED_TRANSLATION_HEIGHT_FRACTION

    private fun navigationScale(): Float =
        if (NavigationTransition.KIND == TransitionKind.Scale) NavTitleTransition.SCALE_ONLY
        else NavTitleTransition.COMBINED_SCALE

    /** 供配置验收断言：调整 NavigationTransition.KIND 时标题确实采用同一类型。 */
    fun kind(): TransitionKind = NavigationTransition.KIND
}
