package com.otokit.noharayh.ui.animation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFrameTest {

    /** Idle 必须落到已提交端点，不能被拖动中的残留进度带偏。 */
    @Test
    fun idleUsesCommittedEndpoint() {
        val mai = frame(selectedMai = true, progress = 0.8f, phase = GamePhase.Idle)
        val chu = frame(selectedMai = false, progress = 0.2f, phase = GamePhase.Idle)

        assertEquals(0f, mai.normalizedProgress, 0.0001f)
        assertEquals(1f, chu.normalizedProgress, 0.0001f)
    }

    /** Dragging/Settling 直接使用真实进度，并夹到 0—1。 */
    @Test
    fun activePhasesUseClampedProgress() {
        assertEquals(
            0.35f,
            frame(selectedMai = true, progress = 0.35f, phase = GamePhase.Dragging).normalizedProgress,
            0.0001f,
        )
        assertEquals(
            1f,
            frame(selectedMai = true, progress = 1.4f, phase = GamePhase.Settling).normalizedProgress,
            0.0001f,
        )
        assertEquals(
            0f,
            frame(selectedMai = false, progress = -0.4f, phase = GamePhase.Dragging).normalizedProgress,
            0.0001f,
        )
    }

    private fun frame(selectedMai: Boolean, progress: Float, phase: GamePhase) = GameFrame(
        selectedMai = selectedMai,
        progress = progress,
        phase = phase,
        windowWidthPx = 1200,
        contentWidthPx = 1100,
        contentHeightPx = 1800,
    )
}
