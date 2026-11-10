package com.otokit.noharayh.ui.animation.chrome

import com.otokit.noharayh.ui.constants.motion.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Test

class TitleTransitionFramesTest {

    /** LM 锚点只取墨迹纵向中心，左缘固定为 0。 */
    @Test
    fun lmPivotUsesInkCenter() {
        assertEquals(40f, TitleTransitionFrames.lmPivotY(10f, 70f), 0.0001f)
        assertEquals(0f, TitleTransitionFrames.lmPivotY(0f, 0f), 0.0001f)
    }

    /** 导航入场与 ghost 退场共用配置类型和幅度。 */
    @Test
    fun navEntryAndGhostShareConfiguredFrame() {
        val entry = TitleTransitionFrames.navEntry(0f, 30f)
        val ghost = TitleTransitionFrames.navGhostExit(0f, -30f)

        assertEquals(30f, entry.translationY, 0.0001f)
        assertEquals(1f, entry.alpha, 0.0001f)
        assertEquals(0f, ghost.translationY, 0.0001f)
        assertEquals(1f, ghost.alpha, 0.0001f)
        assertEquals(TitleTransitionFrames.kind(), TitleTransitionFrames.kind())
    }
}
