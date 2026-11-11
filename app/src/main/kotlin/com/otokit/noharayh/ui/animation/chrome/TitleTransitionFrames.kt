package com.otokit.noharayh.ui.animation.chrome

import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
import com.otokit.noharayh.ui.constants.motion.NavigationTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTitleTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.constants.motion.TransitionKind

/**
 * 标题文字转场的纯数学。
 *
 * LM 锚点固定为组合左边中点：pivotX=0，pivotY=墨迹并集的纵向中心。
 * 导航入场/ghost 退场共用该表，不创建 View/Animator。
 */
object TitleTransitionFrames {

    fun lmPivotY(inkTop: Float, inkBottom: Float): Float =
        (inkTop + inkBottom) / 2f

    fun navEntry(progress: Float, translationY: Float): TransitionFrame =
        TransitionFrames.enter(
            kind = NavigationTransition.KIND,
            progress = progress,
            scaleFrom = navigationScale(),
            translationYFrom = translationY,
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
        return TransitionFrame(
            alpha = TransitionFrames.lerp(from.alpha, target.alpha, progress),
            scale = TransitionFrames.lerp(from.scale, target.scale, progress),
            translationX = TransitionFrames.lerp(from.translationX, target.translationX, progress),
            translationY = TransitionFrames.lerp(from.translationY, target.translationY, progress),
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
