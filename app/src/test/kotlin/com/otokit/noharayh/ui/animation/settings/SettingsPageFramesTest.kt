package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import org.junit.Assert.*
import org.junit.Test

class SettingsPageFramesTest {
    @Test fun unfoldBackRetracesTheSamePageAndTitleFrames() {
        for (step in 0..100) {
            val p = step / 100f
            for (entering in listOf(true, false)) {
                val page = SettingsPageFrames.unfoldPage(p, entering, true, 2.45f)
                val reverse = SettingsPageFrames.unfoldPage(1f - p, !entering, false, 2.45f)
                assertEquals(page.alpha, reverse.alpha, 0.00001f)
                assertEquals(page.translationX, reverse.translationX, 0.0001f)
                val title = SettingsPageFrames.unfoldTitle(p, entering, true, 2.45f)
                val reverseTitle = SettingsPageFrames.unfoldTitle(1f - p, !entering, false, 2.45f)
                assertEquals(title.alpha, reverseTitle.alpha, 0.00001f)
                assertEquals(title.translationX, reverseTitle.translationX, 0.0001f)
                assertEquals(1f, page.scale, 0f)
                assertEquals(1f, title.scale, 0f)
            }
        }
    }

    @Test fun unfoldOpensSpaceBeforeContentAndReleasesItAtTheEndpoint() {
        assertTrue(SettingsPageFrames.unfoldReveal(.2f, true) > 0f)
        assertEquals(0f, SettingsPageFrames.unfoldPage(.2f, true, true, 1f).alpha, 0f)
        assertEquals(1f, SettingsPageFrames.unfoldReveal(1f, true), 0f)
        assertEquals(0f, SettingsPageFrames.unfoldReveal(1f, false), 0f)
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

    @Test fun phoneKeepsRootOpaqueAndStationaryUntilTabletSelectionColorsAreReached() {
        for (step in 0..25) {
            val p = step / 100f
            val pageProgress = SettingsPageFrames.phoneUnfoldProgress(p, true)
            val root = SettingsPageFrames.unfoldPage(pageProgress, false, true, 2.45f)
            val child = SettingsPageFrames.unfoldPage(pageProgress, true, true, 2.45f)
            val title = SettingsPageFrames.unfoldTitle(pageProgress, false, true, 2.45f)
            assertEquals(1f, root.alpha, 0f)
            assertEquals(0f, root.translationX, 0f)
            assertEquals(0f, child.alpha, 0f)
            assertEquals(1f, title.alpha, 0f)
            assertEquals(0f, title.translationX, 0f)
            assertEquals(SettingsPageFrames.selectionChange(p),
                SettingsPageFrames.phoneSelectionChange(p, true), 0f)
        }
        assertEquals(1f, SettingsPageFrames.phoneSelectionChange(.25f, true), 0f)
        assertTrue(SettingsPageFrames.phoneUnfoldProgress(.3f, true) > 0f)
        assertEquals(1f, SettingsPageFrames.phoneUnfoldProgress(1f, true), 0f)
        assertEquals(1f, SettingsPageFrames.phoneUnfoldProgress(1f, false), 0f)
    }

    @Test fun phoneBackRetracesPageAndSelectionIncludingTheColorOnlyPhase() {
        for (step in 0..100) {
            val p = step / 100f
            val outward = SettingsPageFrames.phoneUnfoldProgress(p, true)
            val back = SettingsPageFrames.phoneUnfoldProgress(1f - p, false)
            for (entering in listOf(true, false)) {
                val page = SettingsPageFrames.unfoldPage(outward, entering, true, 2.45f)
                val reverse = SettingsPageFrames.unfoldPage(back, !entering, false, 2.45f)
                assertEquals(page.alpha, reverse.alpha, 0.00001f)
                assertEquals(page.translationX, reverse.translationX, 0.0001f)
            }
            assertEquals(SettingsPageFrames.phoneSelectionChange(p, true),
                1f - SettingsPageFrames.phoneSelectionChange(1f - p, false), 0.00001f)
        }
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
