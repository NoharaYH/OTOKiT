package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SliderDragTicksTest {
    private val ticks = SliderDragTicks()

    @Test fun stationaryFingerNeverProducesTicks() {
        ticks.begin(0.5f, 0L)
        assertNull(ticks.move(0.5f, 200f, 100L))
        assertNull(ticks.move(0.5f, 200f, 1000L))
    }

    @Test fun shortMovesAccumulateIntoOneTooth() {
        ticks.begin(0.5f, 0L)
        assertNull(ticks.move(0.503f, 200f, 20L))
        assertNull(ticks.move(0.506f, 200f, 40L))
        assertEquals(SliderDragTicks.Feedback.Tick, ticks.move(0.522f, 200f, 60L))
    }

    @Test fun fastDraggingIsRateLimitedAndDoesNotQueueVibrationAfterStopping() {
        ticks.begin(0.5f, 0L)
        assertEquals(SliderDragTicks.Feedback.Tick, ticks.move(0.53f, 200f, 40L))
        assertNull(ticks.move(0.56f, 200f, 45L))
        assertNull(ticks.move(0.56f, 200f, 100L))
        assertEquals(SliderDragTicks.Feedback.Tick, ticks.move(0.59f, 200f, 110L))
    }

    @Test fun endpointHasOneDistinctTickAndNoRepeatedBuzz() {
        ticks.begin(0.99f, 0L)
        assertEquals(SliderDragTicks.Feedback.Edge, ticks.move(1f, 200f, 40L))
        assertNull(ticks.move(1f, 200f, 80L))
        assertEquals(SliderDragTicks.Feedback.Tick, ticks.move(0.97f, 200f, 90L))
        assertNull(ticks.move(1f, 200f, 95L))
        assertNull(ticks.move(1f, 200f, 200L))
    }

    @Test fun reversingContributesTravelAndNewPressClearsIt() {
        ticks.begin(0.5f, 0L)
        assertNull(ticks.move(0.512f, 200f, 20L))
        assertEquals(SliderDragTicks.Feedback.Tick, ticks.move(0.5f, 200f, 40L))
        ticks.begin(0.9f, 100L)
        assertNull(ticks.move(0.903f, 200f, 130L))
    }
}
