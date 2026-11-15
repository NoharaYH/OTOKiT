package com.otokit.noharayh.ui.animation.settings

import org.junit.Assert.*
import org.junit.Test

class ThemePreviewFramesTest {
    @Test fun readinessAndStaggerDoNotBlockInputOrRunForever() {
        val frames = ThemePreviewFrames(24)
        frames.geometry(0, 1f); frames.geometry(1, 1f, 1)
        assertFalse(frames.advance(100f)) // 未就绪不转空时钟。
        frames.ready(0); frames.ready(1)
        assertTrue(frames.advance(100f))
        assertTrue(frames.progress[0] > frames.progress[1])
        assertFalse(frames.advance(2000f))
        assertFalse(frames.advance(16f))
    }

    @Test fun shrinkingAndReversingNeverStrandAnUnfinishedEntrance() {
        val frames = ThemePreviewFrames(2)
        frames.geometry(0, 1f); frames.ready(0); frames.advance(300f)
        val current = frames.progress[0]
        frames.geometry(0, 0.5f)
        assertEquals(current, frames.progress[0], 0f)
        assertTrue(frames.advance(100f))
        assertTrue(frames.progress[0] > current)
        val shrinking = frames.progress[0]
        frames.geometry(0, 0.6f)
        assertTrue(frames.advance(100f))
        assertTrue(frames.progress[0] > shrinking)
        assertFalse(frames.advance(1000f))
        assertEquals(1f, frames.progress[0], 0f)
        assertEquals(0f, frames.progress[1], 0f)
    }

    @Test fun completedEntranceStaysStillThroughPartialCollapseAndReopen() {
        val frames = ThemePreviewFrames(1)
        frames.geometry(0, 1f); frames.ready(0); frames.advance(2000f)
        for (expansion in listOf(0.8f, 0.2f, 0.9f, 1f)) {
            frames.geometry(0, expansion)
            assertFalse(frames.advance(100f))
            assertEquals(1f, frames.progress[0], 0f)
        }
    }

    @Test fun fullCollapseReleasesReadinessAndRearmsStaggerForNextEntrance() {
        val frames = ThemePreviewFrames(1)
        frames.geometry(0, 1f); frames.ready(0); frames.finish()
        frames.geometry(0, 0f)
        assertEquals(0f, frames.progress[0], 0f)
        frames.ready(0) // 折叠期间的旧通知不能启动下一轮。
        frames.geometry(0, 0.1f, 2)
        assertFalse(frames.advance(2000f))
        assertEquals(0f, frames.progress[0], 0f)
        frames.ready(0)
        assertTrue(frames.advance(35f))
        assertEquals(0f, frames.progress[0], 0f)
        assertTrue(frames.advance(100f))
        assertTrue(frames.progress[0] in 0.01f..0.99f)
        assertFalse(frames.advance(2000f))
        assertEquals(1f, frames.progress[0], 0f)
    }

    @Test fun aSingleIncomingRowStartsImmediatelyAtEitherEndOfExistingRows() {
        val frames = ThemePreviewFrames(7)
        for (index in 0..5) { frames.geometry(index, 1f, index); frames.ready(index) }
        frames.finish()
        frames.geometry(0, 0f)
        assertTrue(frames.geometry(6, 0.2f))
        frames.ready(6)
        frames.advance(100f)
        val fromBottom = frames.progress[6]
        assertTrue(fromBottom > 0f)
        frames.finish()
        frames.geometry(6, 0f)
        assertTrue(frames.geometry(0, 0.2f))
        frames.ready(0)
        frames.advance(100f)
        assertEquals(fromBottom, frames.progress[0], 0f)
        assertFalse(frames.geometry(0, 0.5f, 5)) // 稳定行不会因重复布局重新错峰。
        assertFalse(frames.advance(1000f))
        assertEquals(1f, frames.progress[0], 0f)
    }

    @Test fun immediateFinishAppliesTargetsAndLayerWindowsStayOrdered() {
        val frames = ThemePreviewFrames(2)
        frames.geometry(0, 1f); frames.ready(0); frames.finish()
        assertEquals(1f, frames.progress[0], 0f)
        assertEquals(0f, frames.progress[1], 0f)
        assertFalse(frames.advance(16f))
        assertTrue(ThemePreviewFrames.layer(0.5f, 0f, 0.5f) > ThemePreviewFrames.layer(0.5f, 0.167f, 1f))
    }
}
