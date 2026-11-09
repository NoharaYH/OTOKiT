package com.otokit.noharayh.ui.shell.background

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackgroundInputGateTest {

    /** 只有初始化完成、resumed、输入允许且归位屏障结束时才允许提交。 */
    @Test
    fun commitsOnlyWhenAllConditionsHold() {
        assertTrue(gate().canCommit)
    }

    /** 任一条件不满足都不提交。 */
    @Test
    fun rejectsWhenAnyConditionMissing() {
        assertFalse(gate(initialized = false).canCommit)
        assertFalse(gate(resumed = false).canCommit)
        assertFalse(gate(inputEnabled = false).canCommit)
        assertFalse(gate(barrierActive = true).canCommit)
    }

    @Test
    fun lateFirstLayoutStillCompletesInitialization() {
        var gate = BackgroundInputGate(resumed = true, inputEnabled = true).beginAlignment()
        val generation = gate.generation
        repeat(12) {
            gate = gate.confirmAlignment(generation, layoutCompleted = false, atEndpoint = true, motionStopped = true)
            assertFalse(gate.canCommit)
        }

        gate = gate.confirmAlignment(generation, layoutCompleted = true, atEndpoint = true, motionStopped = true)

        assertTrue(gate.initialized)
        assertTrue(gate.canCommit)
    }

    @Test
    fun staleGenerationCannotCompleteANewerInterruption() {
        val interrupted = gate().beginAlignment()
        val newer = interrupted.beginAlignment()

        val staleResult = newer.confirmAlignment(interrupted.generation, true, true, true)

        assertEquals(newer, staleResult)
        assertFalse(staleResult.canCommit)
        assertTrue(newer.confirmAlignment(newer.generation, true, true, true).canCommit)
    }

    @Test
    fun cancellationWaitsForActualLayoutEndpointAndStoppedMotion() {
        val cancelled = gate().beginAlignment()
        assertFalse(cancelled.canCommit)
        assertFalse(cancelled.confirmAlignment(cancelled.generation, false, true, true).canCommit)
        assertFalse(cancelled.confirmAlignment(cancelled.generation, true, false, true).canCommit)
        assertFalse(cancelled.confirmAlignment(cancelled.generation, true, true, false).canCommit)
        assertTrue(cancelled.confirmAlignment(cancelled.generation, true, true, true).canCommit)
    }

    private fun gate(
        initialized: Boolean = true,
        resumed: Boolean = true,
        inputEnabled: Boolean = true,
        barrierActive: Boolean = false,
    ): BackgroundInputGate = BackgroundInputGate(
        initialized = initialized,
        resumed = resumed,
        inputEnabled = inputEnabled,
        barrierActive = barrierActive,
    )
}
