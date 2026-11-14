package com.otokit.noharayh.ui.animation.common

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin

class ContinuousCapsuleGeometryTest {
    @Test fun bezierJoinsHaveZeroStartCurvatureAndUnitCircleEndCurvature() {
        for (aspect in listOf(1.001, 1.05, 1.2, 1.4, 2.0, 3.4, 10.0)) {
            val b = capsuleTransition(aspect)
            // P0/P1/P2 have y=0, so the start tangent is horizontal and curvature is zero.
            assertTrue(b.control1 + b.extension > 0.0)
            val dx = 3.0 * (b.endX - b.control2)
            val dy = 3.0 * b.endY
            val ddx = 6.0 * (b.endX - 2.0 * b.control2 + b.control1)
            val ddy = 6.0 * b.endY
            val speed = hypot(dx, dy)
            assertEquals(cos(b.angle), dx / speed, 1e-8)
            assertEquals(sin(b.angle), dy / speed, 1e-8)
            assertEquals("curvature at aspect $aspect", 1.0, (dx * ddy - dy * ddx) / speed.pow(3), 1e-7)
            // The end lies exactly on the retained unit circle centered at (0,1).
            assertEquals(1.0, hypot(b.endX, b.endY - 1.0), 1e-12)
        }
    }

    @Test fun transitionsFitAvailableStraightEdgeAndConvergeToCircle() {
        for (i in 0..1000) {
            val aspect = 1.0 + i / 1000.0
            val b = capsuleTransition(aspect)
            assertTrue(b.extension >= 0.0 && b.extension <= aspect - 1.0 + 1e-12)
            assertTrue(b.control1 <= b.control2 && b.control2 <= b.endX)
        }
        val circle = capsuleTransition(1.0)
        assertEquals(0.0, circle.extension, 0.0)
        assertEquals(0.0, circle.angle, 0.0)
        assertTrue(capsuleTransition(1.000001).angle < 0.00001)
    }
}
