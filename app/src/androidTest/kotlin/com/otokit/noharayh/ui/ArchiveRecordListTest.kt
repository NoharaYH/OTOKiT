package com.otokit.noharayh.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArchiveRecordListTest : UiComponentTestHost() {
    @Test fun selectedRowsShrinkInPlaceBeforeRemainingRowsMove() {
        assumeTrue(ValueAnimator.areAnimatorsEnabled())
        val records = (0 until 18).map { ArchiveRecordList.Row("Fixture $it") {} }
        lateinit var list: ArchiveRecordList
        main {
            list = ArchiveRecordList(activity, "Records")
            list.setRows(records)
            list.setSelection(setOf(records[0], records[2]))
        }
        mount(list, width = 800, height = 1100)
        lateinit var first: SettingsButton
        lateinit var third: SettingsButton
        lateinit var retained: SettingsButton
        var originalTop = 0
        var finished = false
        main {
            val rows = ((list.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup)
            fun record(index: Int) = (0 until rows.childCount)
                .map { rows.getChildAt(it) as SettingsButton }
                .single { it.contentDescription == records[index].label }
            first = record(0)
            retained = record(1)
            third = record(2)
            originalTop = retained.top
            assertTrue(first.isSelected && third.isSelected)
            list.removeRows(setOf(records[0], records[2])) { finished = true }
        }

        var sawShrink = false
        var sawRefill = false
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            main {
                if (first.parent != null && first.alpha in .05f.. .95f) {
                    assertTrue(first.removalInset > 0f)
                    assertEquals(first.removalInset, third.removalInset, 1f)
                    assertEquals(originalTop, retained.top)
                    sawShrink = true
                }
                if (first.parent == null && retained.top in 1 until originalTop) sawRefill = true
            }
            if (finished) break
            SystemClock.sleep(12)
        } while (SystemClock.uptimeMillis() < deadline)

        assertTrue(sawShrink)
        assertTrue(sawRefill)
        assertTrue(finished)
        main {
            assertNull(first.parent)
            assertNull(third.parent)
            assertEquals(0, retained.top)
        }
    }
}
