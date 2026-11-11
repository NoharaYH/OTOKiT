package com.otokit.noharayh.ui.animation.common

import org.junit.Assert.assertEquals
import org.junit.Test

class TransitionTimingTest {
    @Test fun partialGestureUsesOnlyRemainingDistance() {
        assertEquals(105L, TransitionTiming.remainingDuration(420L, 0.75f, 1f))
        assertEquals(225L, TransitionTiming.remainingDuration(300L, 0.75f, 0f))
    }

    @Test fun reversalUsesSameDistanceAndFiniteEndpointDuration() {
        assertEquals(
            TransitionTiming.remainingDuration(300L, 0.2f, 0.8f),
            TransitionTiming.remainingDuration(300L, 0.8f, 0.2f),
        )
        assertEquals(1L, TransitionTiming.remainingDuration(300L, 1f, 1f))
        assertEquals(300L, TransitionTiming.remainingDuration(300L, -0.5f, 1.5f))
    }
}
