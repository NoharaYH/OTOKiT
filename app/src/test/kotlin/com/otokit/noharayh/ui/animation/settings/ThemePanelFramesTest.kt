package com.otokit.noharayh.ui.animation.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePanelFramesTest {
    @Test fun endpointsRestoreTheCollapsedAndExpandedShapes() {
        assertEquals(0f, ThemePanelFrames.extension(0f), 0f)
        assertEquals(0f, ThemePanelFrames.gap(0f), 0f)
        assertEquals(0f, ThemePanelFrames.menu(0f), 0f)
        assertEquals(1f, ThemePanelFrames.extension(1f), 0f)
        assertEquals(1f, ThemePanelFrames.gap(1f), 0f)
        assertEquals(1f, ThemePanelFrames.menu(1f), 0f)
    }

    @Test fun menuWaitsForSpaceAndReversesBeforeTheContainerCloses() {
        for (step in 0..100) {
            val p = step / 100f
            assertTrue(ThemePanelFrames.menu(p) <= ThemePanelFrames.extension(p))
            if (p <= 0.6f) assertEquals(0f, ThemePanelFrames.menu(p), 0f)
            if (p <= 0.15f) assertEquals(0f, ThemePanelFrames.gap(p), 0f)
        }
        // 预测返回可随手指往返；同一位置始终得到同一帧，不依赖动画结束回调。
        val forward = listOf(0f, .3f, .8f, .5f, 1f).map(ThemePanelFrames::extension)
        assertTrue(forward[2] > forward[3])
        assertEquals(1f, forward.last(), 0f)
    }
}
