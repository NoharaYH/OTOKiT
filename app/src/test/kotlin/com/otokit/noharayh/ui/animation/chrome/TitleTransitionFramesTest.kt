package com.otokit.noharayh.ui.animation.chrome

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleTransitionFramesTest {

    /** LM 锚点只取墨迹纵向中心，左缘固定为 0。 */
    @Test
    fun lmPivotUsesInkCenter() {
        assertEquals(40f, TitleTransitionFrames.lmPivotY(10f, 70f), 0.0001f)
        assertEquals(0f, TitleTransitionFrames.lmPivotY(0f, 0f), 0.0001f)
    }

    /** 默认标题导航同时位移与淡变：ghost 从可见开始，新字从透明开始。 */
    @Test
    fun navEntryAndGhostFadeAtStart() {
        val entry = TitleTransitionFrames.navEntry(0f, 30f)
        val ghost = TitleTransitionFrames.navGhostExit(0f, -30f)

        assertEquals(30f, entry.translationY, 0.0001f)
        assertEquals(0f, entry.alpha, 0.0001f)
        assertEquals(0f, ghost.translationY, 0.0001f)
        assertEquals(1f, ghost.alpha, 0.0001f)
    }

    /** 到端点时新字完全可见，ghost 完全透明。 */
    @Test
    fun navEndsAtStableFrames() {
        val entry = TitleTransitionFrames.navEntry(1f, 30f)
        val ghost = TitleTransitionFrames.navGhostExit(1f, -30f)

        assertEquals(0f, entry.translationY, 0.0001f)
        assertEquals(1f, entry.alpha, 0.0001f)
        assertEquals(-30f, ghost.translationY, 0.0001f)
        assertEquals(0f, ghost.alpha, 0.0001f)
    }
}
