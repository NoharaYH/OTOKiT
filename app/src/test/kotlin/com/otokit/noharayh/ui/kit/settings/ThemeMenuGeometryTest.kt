package com.otokit.noharayh.ui.kit.settings

import org.junit.Assert.*
import org.junit.Test

class ThemeMenuGeometryTest {
    private val menu = ThemeMenuGeometry(17, 5, 44f, 4f, 6f, 8f)

    @Test fun releaseSnapsToWholeRowsAndClampsBothEnds() {
        assertEquals(0f, menu.snap(-30f), 0f)
        assertEquals(0f, menu.snap(23f), 0f)
        assertEquals(48f, menu.snap(25f), 0f)
        assertEquals(12 * 48f, menu.snap(9999f), 0f)
        for (offset in -100..1000) {
            val snapped = menu.snap(offset.toFloat())
            assertTrue(snapped in 0f..menu.maximum)
            assertEquals(0f, snapped % menu.stride, 0f)
        }
    }

    @Test fun normalRowsKeepTheirHeightAndEdgeRowsCollapseWithoutOverlap() {
        val offset = 4 * menu.stride
        val (top, bottom) = menu.bounds(4, offset)
        assertEquals(44f, bottom - top, 0.001f)
        val before = menu.bounds(3, offset)
        val after = menu.bounds(9, offset)
        assertEquals(6f, before.second - before.first, 0.001f)
        assertEquals(6f, after.second - after.first, 0.001f)
        var previousBottom = Float.NEGATIVE_INFINITY
        for (index in 0 until menu.count) {
            val bounds = menu.bounds(index, offset)
            assertTrue(bounds.first >= previousBottom)
            assertTrue(bounds.second >= bounds.first)
            assertTrue(bounds.first >= 8f)
            assertTrue(bounds.second <= menu.height - 8f)
            previousBottom = bounds.second
        }
    }

    @Test fun everyIntermediateFrameChangesExactlyOneOutgoingAndOneIncomingRow() {
        for (step in 0 until menu.count - menu.slots) {
            for (fraction in listOf(0.1f, 0.25f, 0.5f, 0.75f, 0.9f)) {
                val offset = (step + fraction) * menu.stride
                val partial = (0 until menu.count).filter { menu.expansion(it, offset) in 0.001f..0.999f }
                assertEquals(listOf(step, step + menu.slots), partial)
                assertEquals(1f, partial.sumOf { menu.expansion(it, offset).toDouble() }.toFloat(), 0.001f)
                for (i in 0 until menu.count) {
                    val expected = when {
                        i == step -> 44f - 38f * fraction
                        i == step + menu.slots -> 6f + 38f * fraction
                        i in step + 1 until step + menu.slots -> 44f
                        else -> 6f
                    }
                    val (top, bottom) = menu.bounds(i, offset)
                    assertEquals(expected, bottom - top, 0.001f)
                    if (i > 0) assertEquals(4f, top - menu.bounds(i - 1, offset).second, 0.001f)
                }
                assertEquals(8f, menu.bounds(0, offset).first, 0.001f)
                assertEquals(menu.height - 8f, menu.bounds(menu.count - 1, offset).second, 0.001f)
            }
        }
    }

    @Test fun boundaryBounceDoesNotDeformExtraRows() {
        for (i in 0 until menu.count) {
            assertEquals(menu.bounds(i, 0f), menu.bounds(i, -30f))
            assertEquals(menu.bounds(i, menu.maximum), menu.bounds(i, menu.maximum + 30f))
        }
    }

    @Test fun firstAndLastContainersExpandAtOppositeEnds() {
        assertEquals(1f, menu.expansion(0, 0f), 0f)
        assertEquals(0.5f, menu.expansion(0, menu.stride / 2f), 0f)
        assertEquals(0f, menu.expansion(0, menu.stride), 0f)
        assertEquals(0f, menu.expansion(menu.count - 1, menu.maximum - menu.stride), 0f)
        assertEquals(0.5f, menu.expansion(menu.count - 1, menu.maximum - menu.stride / 2f), 0f)
        assertEquals(1f, menu.expansion(menu.count - 1, menu.maximum), 0f)
    }

    @Test fun crossingCompressionBoundaryIsContinuous() {
        val left = menu.bounds(4, 4 * menu.stride - 0.001f)
        val right = menu.bounds(4, 4 * menu.stride + 0.001f)
        assertEquals(left.first, right.first, 0.01f)
        assertEquals(left.second, right.second, 0.01f)
    }

    @Test fun firstRowStartsNextToTriggerWithoutEmptyTopPreview() {
        assertEquals(8f, menu.bounds(0, 0f).first, 0f)
    }

    @Test fun shortMenuHasNoScrollingOrPreviewSpace() {
        val short = ThemeMenuGeometry(3, 3, 44f, 4f, 0f, 8f)
        assertEquals(0f, short.maximum, 0f)
        assertEquals(8f, short.bounds(0, 0f).first, 0f)
        assertEquals(short.height - 8f, short.bounds(2, 0f).second, 0f)
    }

    @Test fun feedbackOccursAtVisibleFullHeightWithoutWaitingForSpringRest() {
        val feedback = ThemeMenuExpansionFeedback(menu)
        assertFalse(feedback.update(0f))
        assertFalse(feedback.update(menu.stride * 0.5f))
        assertFalse(feedback.update(menu.stride * 0.95f))
        // 43.62px 已绘制为完整的 44px，此时弹簧仍可能继续运行。
        assertTrue(feedback.update(menu.stride * 0.99f))
        assertFalse(feedback.update(menu.stride))
    }

    @Test fun springWobbleAndRepeatedFramesDoNotRepeatTheSameRowFeedback() {
        val feedback = ThemeMenuExpansionFeedback(menu)
        assertTrue(feedback.update(menu.stride))
        for (fraction in listOf(1.02f, 0.98f, 1.001f, 0.999f, 1f, 1f)) {
            assertFalse(feedback.update(menu.stride * fraction))
        }
    }

    @Test fun eachFollowingRowAndReverseExpansionProduceFeedback() {
        val feedback = ThemeMenuExpansionFeedback(menu)
        assertTrue(feedback.update(menu.stride))
        assertTrue(feedback.update(menu.stride * 2f))
        assertTrue(feedback.update(menu.stride * 3f))
        assertFalse(feedback.update(menu.stride * 2.5f))
        assertTrue(feedback.update(menu.stride * 2f))
        assertFalse(feedback.update(menu.stride * 2f))
    }

    @Test fun returningFromPartialDragSignalsTheRowThatActuallyReexpands() {
        val feedback = ThemeMenuExpansionFeedback(menu)
        assertFalse(feedback.update(menu.stride * 0.4f))
        assertTrue(feedback.update(0f))
        assertFalse(feedback.update(-20f))
        assertFalse(feedback.update(0f))
    }
}
