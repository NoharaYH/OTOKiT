package com.otokit.noharayh.ui.shell.navigation

import com.otokit.noharayh.ui.shell.navigation.BackPreviewSession.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class BackPreviewSessionTest {
    @Test fun idleAllowsOrdinaryBackWithoutBegin() {
        val session = BackPreviewSession()
        assertFalse(session.awaitingTerminal)
        assertNull(session.take())
        session.update(0.5f)
        assertEquals(Target.None, session.target)
    }

    @Test fun activeTargetStaysLockedAndProgressCanReverse() {
        val session = BackPreviewSession()
        session.begin(Target.Settings)
        session.update(0.8f)
        session.begin(Target.Drawer)
        assertEquals(Target.Settings, session.target)
        assertEquals(0.8f, session.progress, 0f)
        session.update(0.3f)
        assertEquals(BackPreviewSession.Preview(Target.Settings, 0.3f), session.take())
        assertFalse(session.awaitingTerminal)
        assertNull(session.take())
    }

    @Test fun invalidatedFinishCannotReturnATarget() {
        for (target in listOf(Target.Settings, Target.Drawer)) {
            val session = BackPreviewSession()
            session.begin(target)
            session.update(0.7f)
            assertEquals(target, session.invalidate())
            session.update(1f)
            assertTrue(session.awaitingTerminal)
            assertEquals(Target.None, session.target)
            assertNull(session.take())
            assertFalse(session.awaitingTerminal)
        }
    }

    @Test fun repeatedInvalidationRetainsThePendingTerminalEvent() {
        val session = BackPreviewSession()
        session.begin(Target.Drawer)
        session.invalidate()
        assertEquals(Target.None, session.invalidate())
        assertTrue(session.awaitingTerminal)
        assertNull(session.take())
        assertFalse(session.awaitingTerminal)
    }

    @Test fun invalidatedCancelEndsTheSessionAndOrdinaryBackIsAvailableAgain() {
        val session = BackPreviewSession()
        session.begin(Target.Settings)
        session.invalidate()
        assertNull(session.take())
        assertFalse(session.awaitingTerminal)
    }

    @Test fun nextBeginReplacesAnInvalidatedGesture() {
        val session = BackPreviewSession()
        session.begin(Target.Drawer)
        session.invalidate()
        session.begin(Target.Settings)
        assertEquals(0f, session.progress, 0f)
        session.update(0.6f)
        assertEquals(BackPreviewSession.Preview(Target.Settings, 0.6f), session.take())
    }

    @Test fun invalidatingIdleDoesNotSwallowOrdinaryBack() {
        val session = BackPreviewSession()
        session.invalidate()
        assertFalse(session.awaitingTerminal)
        session.begin(Target.None)
        assertFalse(session.awaitingTerminal)
    }

    @Test fun disposingClearsTargetProgressAndInvalidation() {
        val session = BackPreviewSession()
        session.begin(Target.Settings)
        session.update(0.5f)
        session.invalidate()
        session.reset()
        assertFalse(session.awaitingTerminal)
        assertEquals(Target.None, session.target)
        assertEquals(0f, session.progress, 0f)
    }

    @Test fun repeatedInterruptedAndCompletedGesturesDoNotLeakTargets() {
        val session = BackPreviewSession()
        val random = Random(20261111)
        repeat(10_000) { index ->
            val target = if (index % 2 == 0) Target.Settings else Target.Drawer
            session.begin(target)
            repeat(8) { session.update(random.nextFloat() * 2f - 0.5f) }
            val lastProgress = session.progress
            assertTrue(lastProgress in 0f..1f)
            if (index % 3 != 0) {
                session.invalidate()
                repeat(4) {
                    session.invalidate()
                    session.update(random.nextFloat())
                }
                assertTrue(session.awaitingTerminal)
                assertNull(session.take())
            } else {
                assertEquals(BackPreviewSession.Preview(target, lastProgress), session.take())
            }
            assertFalse(session.awaitingTerminal)
            assertNull(session.take())
        }
    }
}
