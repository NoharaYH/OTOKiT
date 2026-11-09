package com.otokit.noharayh.ui.chrome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerMotionStateTest {
    @Test
    fun backDuringDragRejectsTheRestOfThePointerStream() {
        val state = DrawerMotionState()
        state.beginDrag()
        state.moveTo(0.8f)
        state.request(false)
        state.finishSettle()

        assertFalse(state.beginDrag())
        state.moveTo(0.9f)
        assertEquals(0f, state.progress)
        assertTrue(state.blocksInput)

        state.endStream()
        assertFalse(state.blocksInput)
        assertTrue(state.beginDrag())
    }

    @Test
    fun cancellingPastHalfwayRestoresClosedOrigin() {
        val state = DrawerMotionState()
        state.beginDrag()
        state.moveTo(0.9f)
        state.cancelDrag()
        assertEquals(false, state.targetOpen)
        state.finishSettle()
        state.endStream()

        assertEquals(0f, state.progress)
        assertFalse(state.settledOpen)
        assertFalse(state.blocksInput)
    }

    @Test
    fun cancellingAClosingDragRestoresOpenOrigin() {
        val state = DrawerMotionState()
        state.request(true)
        state.finishSettle()
        state.beginDrag()
        state.moveTo(0.1f)
        state.cancelDrag()
        state.finishSettle()
        state.endStream()

        assertEquals(1f, state.progress)
        assertTrue(state.settledOpen)
        assertTrue(state.blocksInput)
    }

    @Test
    fun forcedCloseOverridesTheDragOriginButKeepsOldInputBlocked() {
        val state = DrawerMotionState()
        state.request(true)
        state.finishSettle()
        state.beginDrag()
        state.forceClose()
        state.finishSettle() // 过期的 Idle 没有目标，不能重新打开。

        assertEquals(0f, state.progress)
        assertFalse(state.settledOpen)
        assertTrue(state.blocksInput)
        state.endStream()
        assertFalse(state.blocksInput)
    }

    @Test
    fun openingAndClosingKeepInputBlockedUntilTheMotionFinishes() {
        val state = DrawerMotionState()
        state.request(true)
        assertEquals(0f, state.progress)
        assertTrue(state.blocksInput)
        state.finishSettle()
        state.request(false)
        state.moveTo(0f)
        assertTrue(state.blocksInput)
        state.finishSettle()
        assertFalse(state.blocksInput)
    }
}
