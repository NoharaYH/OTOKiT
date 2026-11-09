package com.otokit.noharayh.ui.shell.background

import org.junit.Assert.assertFalse
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
