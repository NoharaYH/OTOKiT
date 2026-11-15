package com.otokit.noharayh.ui.animation.settings

import org.junit.Assert.*
import org.junit.Test

class ThemePreviewFramesTest {
    @Test fun readinessAndStaggerDoNotBlockInputOrRunForever() {
        val frames = ThemePreviewFrames(24)
        frames.geometry(0, 1f); frames.geometry(1, 1f)
        assertFalse(frames.advance(100f)) // 未就绪不转空时钟。
        frames.ready(0); frames.ready(1, 1)
        assertTrue(frames.advance(100f))
        assertTrue(frames.progress[0] > frames.progress[1])
        assertFalse(frames.advance(2000f))
        assertFalse(frames.advance(16f))
    }

    @Test fun reversingUsesCurrentFrameAndStableNeighborsDoNotReplay() {
        val frames = ThemePreviewFrames(2)
        frames.geometry(0, 1f); frames.ready(0); frames.advance(300f)
        val current = frames.progress[0]
        frames.geometry(0, 0.5f)
        assertEquals(current, frames.progress[0], 0f)
        frames.advance(100f)
        assertTrue(frames.progress[0] < current)
        val reverse = frames.progress[0]
        frames.geometry(0, 0.6f)
        assertEquals(reverse, frames.progress[0], 0f)
        frames.advance(100f)
        assertTrue(frames.progress[0] > reverse)
        assertEquals(0f, frames.progress[1], 0f)
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
