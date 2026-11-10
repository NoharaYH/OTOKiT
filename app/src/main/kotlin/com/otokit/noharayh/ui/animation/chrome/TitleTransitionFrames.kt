package com.otokit.noharayh.ui.animation.chrome

import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavTitleTransition
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
            kind = NavTitleTransition.KIND,
            progress = progress,
            translationYFrom = translationY,
        )

    fun navGhostExit(progress: Float, translationY: Float): TransitionFrame =
        TransitionFrames.exit(
            kind = NavTitleTransition.KIND,
            progress = progress,
            translationYTo = translationY,
        )

    /** 供配置验收断言：调整 NavTitleTransition.KIND 时标题确实采用同一类型。 */
    fun kind(): TransitionKind = NavTitleTransition.KIND
}
