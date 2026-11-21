package com.otokit.noharayh.ui.animation.chrome

import org.junit.Assert.assertEquals
import org.junit.Test

class DrawerSettleTimingTest {
    @Test fun earlyFlingAndThresholdUseTheSameOpeningTime() {
        assertEquals(320, DrawerSettleTiming.gestureOpen(260, 200))
        assertEquals(320, DrawerSettleTiming.gestureOpen(200, 200))
        assertEquals(200, DrawerSettleTiming.gestureOpen(100, 200))
    }

    @Test fun closingUsesItsOwnDurationEvenForFastFlings() {
        assertEquals(340, DrawerSettleTiming.duration(-300, 300, 0f))
        assertEquals(160, DrawerSettleTiming.duration(-150, 300, -6000f))
        assertEquals(280, DrawerSettleTiming.duration(300, 300, 0f))
    }
}
