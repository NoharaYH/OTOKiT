package com.otokit.noharayh.ui.animation.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PressGeometryTest {
    @Test fun pressedShapeUsesInsetShortSideAndPreservesExpandedBottom() {
        val frame = PressGeometry.resolve(200f, 300f, 52f, 1f, 3f, 16f, 4f, 4f, 0f, 0f)
        assertEquals(3f, frame.inset, 0f)
        assertEquals(23f, frame.top, 0f)
        assertEquals(4f, frame.bottom, 0f)
        assertEquals(23f, frame.haloBottom, 0f)
    }
    @Test fun restRestoresIndependentGroupCorners() {
        val frame = PressGeometry.resolve(200f, 52f, 52f, 0f, 3f, 16f, 16f, 4f, 1f, 0f)
        assertEquals(16f, frame.top, 0f)
        assertEquals(4f, frame.bottom, 0f)
        assertEquals(0f, frame.inset, 0f)
    }
    @Test fun tinyBoundsDoNotProduceNegativeCornerOrThrowForFillInset() {
        val frame = PressGeometry.resolve(2f, 2f, 2f, 1f, 3f, 16f, null, null, 1f, 2f)
        assertTrue(frame.top >= 0f)
        assertEquals(0f, frame.fillInset, 0f)
    }
}
