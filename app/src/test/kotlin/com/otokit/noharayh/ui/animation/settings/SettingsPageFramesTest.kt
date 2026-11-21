package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import org.junit.Assert.*
import org.junit.Test

class SettingsPageFramesTest {
    @Test fun predictiveBackProgressGetsHeavierNearTheEnd() {
        assertEquals(0f, SettingsPageFrames.resistedBackProgress(0f), 0f)
        assertEquals(.6f, SettingsPageFrames.resistedBackProgress(.6f), 0f)
        val middle = SettingsPageFrames.resistedBackProgress(.8f)
        val end = SettingsPageFrames.resistedBackProgress(1f)
        assertTrue(middle > .6f && end > middle && end < 1f)
        assertTrue(end - middle < middle - .6f)
    }

    @Test fun switchingSectionsKeepsTheOutgoingPairCompactAndTheIncomingSectionWide() {
        val compact = SettingsPageFrames.splitPanes(901, 12, 1f)
        val wide = SettingsPageFrames.splitPanes(901, 12, 0f)
        for (step in 0..100) {
            val progress = step / 100f
            val departing = SettingsPageFrames.splitArchiveProgress(progress, fromArchive = true,
                toArchive = false, switchesSection = true, entering = false)
            val arriving = SettingsPageFrames.splitArchiveProgress(progress, fromArchive = true,
                toArchive = false, switchesSection = true, entering = true)
            assertEquals(compact, SettingsPageFrames.splitPanes(901, 12, departing))
            assertEquals(wide, SettingsPageFrames.splitPanes(901, 12, arriving))
            assertEquals(1f - progress, SettingsPageFrames.splitArchiveProgress(progress, fromArchive = true,
                toArchive = false, switchesSection = false, entering = false), 0.00001f)
        }
    }

    @Test fun archiveAndSecondaryShareTheSameHorizontalBoundaryThroughoutTheTransition() {
        val width = 901
        val gap = 12
        val closed = SettingsPageFrames.splitPanes(width, gap, 0f)
        val open = SettingsPageFrames.splitPanes(width, gap, 1f)
        assertEquals(width, closed.archiveLeft)
        assertEquals(width, closed.secondaryLeft + closed.secondaryWidth)
        assertEquals(gap, open.secondaryLeft - open.rootWidth)
        assertEquals(gap, open.archiveLeft - open.secondaryLeft - open.secondaryWidth)
        assertEquals(width, open.archiveLeft + open.archiveWidth)
        assertEquals(open.rootWidth, open.secondaryWidth)
        val frames = (0..100).map { SettingsPageFrames.splitPanes(width, gap, it / 100f) }
        frames.forEach {
            assertEquals(closed.secondaryLeft, it.secondaryLeft)
            assertEquals(open.archiveWidth, it.archiveWidth)
            assertTrue(it.archiveLeft - it.secondaryLeft - it.secondaryWidth in 0..gap)
        }
        frames.zipWithNext().forEach { (before, after) ->
            assertTrue(after.secondaryWidth <= before.secondaryWidth)
            assertTrue(after.archiveLeft <= before.archiveLeft)
        }
    }

    @Test fun unfoldBackRetracesTheSamePageAndTitleFrames() {
        for (step in 0..100) {
            val p = step / 100f
            for (entering in listOf(true, false)) {
                val page = SettingsPageFrames.unfoldPage(p, entering, true, 2.45f)
                val reverse = SettingsPageFrames.unfoldPage(1f - p, !entering, false, 2.45f)
                assertEquals(page.alpha, reverse.alpha, 0.00001f)
                assertEquals(page.translationX, reverse.translationX, 0.0001f)
                assertEquals(0f, page.translationY, 0f)
                assertEquals(0f, reverse.translationY, 0f)
                val title = SettingsPageFrames.unfoldTitle(p, entering, true, 2.45f)
                val reverseTitle = SettingsPageFrames.unfoldTitle(1f - p, !entering, false, 2.45f)
                assertEquals(title.alpha, reverseTitle.alpha, 0.00001f)
                assertEquals(title.translationX, reverseTitle.translationX, 0.0001f)
                assertEquals(1f, page.scale, 0f)
                assertEquals(1f, title.scale, 0f)
            }
        }
    }

    @Test fun unfoldFadesInAndSettlesAtTheVisibleEndpoints() {
        assertEquals(0f, SettingsPageFrames.unfoldPage(.2f, true, true, 1f).alpha, 0f)
        val partialAlpha = SettingsPageFrames.unfoldPage(.5f, true, true, 1f).alpha
        assertTrue(partialAlpha > 0f && partialAlpha < 1f)
        for (frame in listOf(SettingsPageFrames.unfoldPage(1f, true, true, 1f),
            SettingsPageFrames.unfoldTitle(1f, true, false, 1f))) {
            assertEquals(1f, frame.alpha, 0f)
            assertEquals(1f, frame.scale, 0f)
            assertEquals(0f, frame.translationX, 0.0001f)
            assertEquals(0f, frame.translationY, 0.0001f)
        }
    }

    @Test fun selectionChangesInFirstQuarterAndReversesContinuously() {
        assertEquals(0f, SettingsPageFrames.selectionChange(0f), 0f)
        assertEquals(0.5f, SettingsPageFrames.selectionChange(0.125f), 0.0001f)
        assertEquals(1f, SettingsPageFrames.selectionChange(0.25f), 0f)
        assertEquals(1f, SettingsPageFrames.selectionChange(1f), 0f)
        val outward = (0..100).map { SettingsPageFrames.selectionChange(it / 100f) }
        val reverse = (100 downTo 0).map { SettingsPageFrames.selectionChange(it / 100f) }
        assertEquals(outward, reverse.reversed())
    }

    @Test fun phoneBackChangesColorsThroughoutThePageExitAndCanReverseForCancellation() {
        val samples = (0..100).map { it / 100f }
        for (p in samples + samples.reversed()) {
            assertEquals(p, SettingsPageFrames.phoneSelectionChange(p, false), 0.00001f)
        }
        assertTrue(SettingsPageFrames.phoneSelectionChange(.1f, false) > 0f)
        assertTrue(SettingsPageFrames.phoneSelectionChange(.9f, false) < 1f)
    }

    @Test fun phoneBackRevealsRootWithoutChangingItsSize() {
        val start = SettingsPageFrames.page(0f, true, false, false, 400f)
        val half = SettingsPageFrames.page(0.5f, false, false, false, 400f)
        val end = SettingsPageFrames.page(1f, true, false, false, 400f)
        assertEquals(-400f, start.translationX, 0f)
        assertEquals(200f, half.translationX, 0f)
        assertEquals(0f, end.translationX, 0f)
        assertEquals(1f, half.scale, 0f)
    }
    @Test fun phoneTitleUsesExactPageDistanceAndDirection() {
        val entering = SettingsPageFrames.title(0f, true, true, false, 400f)
        val entered = SettingsPageFrames.title(1f, true, true, false, 400f)
        val exiting = SettingsPageFrames.title(1f, false, true, false, 400f)

        assertEquals(400f, entering.translationX, 0f)
        assertEquals(0f, entered.translationX, 0f)
        assertEquals(-400f, exiting.translationX, 0f)
        assertEquals(1f, entering.alpha, 0f)
        assertEquals(1f, exiting.alpha, 0f)
        assertEquals(1f, entering.scale, 0f)
    }
    @Test fun splitPageAndTitleShareVisibleEndpoints() {
        for (forward in listOf(true, false)) {
            val page = SettingsPageFrames.page(0f, true, forward, true, 400f)
            val title = SettingsPageFrames.title(0f, true, forward, true, 400f)
            assertEquals(0f, page.alpha, 0f)
            assertEquals(0f, title.alpha, 0f)
            assertEquals(1f, SettingsPageFrames.page(1f, true, forward, true, 400f).alpha, 0f)
            assertEquals(1f, SettingsPageFrames.title(1f, true, forward, true, 400f).scale, 0f)
        }
    }
}
