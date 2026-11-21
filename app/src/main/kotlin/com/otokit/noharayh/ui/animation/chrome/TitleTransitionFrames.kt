package com.otokit.noharayh.ui.animation.chrome

import com.otokit.noharayh.ui.animation.shell.SettingsTransitionFrames
import com.otokit.noharayh.ui.animation.common.PageTurnFrames
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition

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
        TransitionFrames.pagerPair(PagerPairTransition.KIND, progress, width)

    fun navEntry(progress: Float, translationY: Float) = PageTurnFrames.enter(progress, dy = translationY)

    fun navGhostExit(progress: Float, translationY: Float,
        from: TransitionFrame = TransitionFrame(1f, 1f, 0f, 0f),
    ) = PageTurnFrames.exit(progress, dy = translationY, from = from)

    fun settingsFeature(progress: Float) = SettingsTransitionFrames.feature(progress)
    fun settingsOverlay(progress: Float, distance: Float) = SettingsTransitionFrames.overlay(progress, distance)
}
