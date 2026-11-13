package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class RelativeSliderDragTest {
    private val drag = RelativeSliderDrag(8f)

    @Test fun touchFarFromThumbAndSmallJitterDoNotSeek() {
        drag.begin(800f, 20f, 0.25f, 200f, false)
        assertEquals(0.25f, drag.fraction, 0.0001f)
        assertEquals(0.25f, drag.move(807f, 23f), 0.0001f)
        assertEquals(RelativeSliderDrag.Phase.Pressed, drag.phase)
        drag.end()
        assertEquals(0.25f, drag.move(900f, 20f), 0.0001f)
    }

    @Test fun equalMovementHasEqualEffectRegardlessOfWhereFingerLands() {
        for (origin in listOf(0f, 100f, 800f)) {
            drag.begin(origin, 20f, 0.25f, 200f, false)
            assertEquals(0.46f, drag.move(origin + 50f, 20f), 0.0001f)
            assertEquals(0.56f, drag.move(origin + 70f, 20f), 0.0001f)
        }
    }

    @Test fun crossingDragThresholdOnlyConsumesDistanceBeyondSlop() {
        drag.begin(0f, 0f, 0.5f, 200f, false)
        assertEquals(0.5f, drag.move(8f, 0f), 0.0001f)
        assertEquals(0.505f, drag.move(9f, 0f), 0.0001f)
        assertEquals(RelativeSliderDrag.Phase.Dragging, drag.phase)
    }

    @Test fun verticalIntentNeverChangesValueEvenIfFingerLaterMovesSideways() {
        drag.begin(0f, 0f, 0.5f, 200f, false)
        assertEquals(0.5f, drag.move(2f, 20f), 0.0001f)
        assertEquals(RelativeSliderDrag.Phase.Vertical, drag.phase)
        assertEquals(0.5f, drag.move(100f, 20f), 0.0001f)
    }

    @Test fun reversingAfterOvershootingEitherEndRespondsImmediately() {
        drag.begin(0f, 0f, 0.9f, 100f, false)
        assertEquals(1f, drag.move(100f, 0f), 0.0001f)
        assertEquals(0.95f, drag.move(95f, 0f), 0.0001f)
        assertEquals(0f, drag.move(-200f, 0f), 0.0001f)
        assertEquals(0.05f, drag.move(-195f, 0f), 0.0001f)
    }

    @Test fun rtlInvertsPhysicalDirection() {
        drag.begin(0f, 0f, 0.5f, 200f, true)
        assertEquals(0.4f, drag.move(28f, 0f), 0.0001f)
        assertEquals(0.5f, drag.move(8f, 0f), 0.0001f)
    }

    @Test fun pointerHandoffReanchorsWithoutChangingValue() {
        drag.begin(0f, 0f, 0.5f, 100f, false)
        assertEquals(0.7f, drag.move(28f, 0f), 0.0001f)
        drag.reanchor(500f, 20f)
        assertEquals(0.7f, drag.move(500f, 20f), 0.0001f)
        assertEquals(0.8f, drag.move(510f, 20f), 0.0001f)
    }

    @Test fun newTouchStartsFromNewValueWithoutCarryingPreviousTravel() {
        drag.begin(0f, 0f, 0.5f, 100f, false)
        drag.move(40f, 0f)
        drag.end()
        drag.begin(700f, 20f, 0.2f, 100f, false)
        assertEquals(0.2f, drag.fraction, 0.0001f)
        assertEquals(0.3f, drag.move(718f, 20f), 0.0001f)
    }
}
