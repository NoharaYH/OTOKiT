package com.otokit.noharayh.ui.animation.settings

import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import org.junit.Assert.*
import org.junit.Test

class SettingsPageFramesTest {
    @Test fun selectionChangesInFirstQuarterAndReversesContinuously() {
        assertEquals(0f, SettingsPageFrames.selectionChange(0f), 0f)
        assertEquals(0.5f, SettingsPageFrames.selectionChange(0.125f), 0.0001f)
        assertEquals(1f, SettingsPageFrames.selectionChange(0.25f), 0f)
        assertEquals(1f, SettingsPageFrames.selectionChange(1f), 0f)
        val outward = (0..100).map { SettingsPageFrames.selectionChange(it / 100f) }
        val reverse = (100 downTo 0).map { SettingsPageFrames.selectionChange(it / 100f) }
        assertEquals(outward, reverse.reversed())
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
    @Test fun splitPageAndTitleShareVisibleEndpoints() {
        for (forward in listOf(true, false)) {
            val page = SettingsPageFrames.page(0f, true, forward, true, 400f)
            val title = SettingsPageFrames.title(0f, true, forward, 48f)
            assertEquals(0f, page.alpha, 0f)
            assertEquals(0f, title.alpha, 0f)
            assertEquals(1f, SettingsPageFrames.page(1f, true, forward, true, 400f).alpha, 0f)
            assertEquals(1f, SettingsPageFrames.title(1f, true, forward, 48f).scale, 0f)
        }
    }
}
