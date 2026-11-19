package com.otokit.noharayh.ui

import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordList
import com.otokit.noharayh.ui.kit.settings.ArchiveRecordRow
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
        lateinit var first: ArchiveRecordRow
        lateinit var third: ArchiveRecordRow
        lateinit var retained: ArchiveRecordRow
        var originalTop = 0
        var finished = false
        main {
            first = row(list, records[0])
            retained = row(list, records[1])
            third = row(list, records[2])
            originalTop = retained.top
            assertTrue(first.button.isSelected && third.button.isSelected)
            list.removeRows(setOf(records[0], records[2])) { finished = true }
        }

        var sawShrink = false
        var sawRefill = false
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            main {
                if (first.parent != null && first.alpha in .05f.. .95f) {
                    assertTrue(first.button.removalInset > 0f)
                    assertEquals(first.button.removalInset, third.button.removalInset, 1f)
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

    @Test fun horizontalGesturesRevealOppositeSideAndSelectAnInclusiveRange() {
        val records = (0 until 7).map { ArchiveRecordList.Row("Fixture $it") { fail("Drag became a tap") } }
        lateinit var list: ArchiveRecordList
        var selection = setOf(records[0])
        val accepted = mutableListOf<Set<ArchiveRecordList.Row>>()
        main {
            list = ArchiveRecordList(activity, "Records") { range, select ->
                assertTrue(select)
                accepted += range
                selection += range
                list.setSelection(selection)
            }
            list.setRows(records)
            list.setSelection(selection)
        }
        mount(list, width = 800, height = 1450)
        val density = activity.resources.displayMetrics.density
        val slop = ViewConfiguration.get(activity).scaledTouchSlop
        val x = 380f
        fun gesture(index: Int, block: (Float) -> Unit) {
            var y = 0f
            main { y = row(list, records[index]).let { it.top + it.height / 2f } }
            touch(list, MotionEvent.ACTION_DOWN, Triple(0, x, y))
            block(y)
        }
        fun send(action: Int, dx: Float, y: Float) = touch(list, action, Triple(0, x + dx, y))
        val threshold = slop + 48f * density
        gesture(2) { y ->
            send(MotionEvent.ACTION_MOVE, slop * 1.2f, y + slop * .5f)
            send(MotionEvent.ACTION_UP, slop * 1.2f, y + slop * .5f)
        }
        gesture(3) { y ->
            send(MotionEvent.ACTION_MOVE, -threshold / 2, y)
            send(MotionEvent.ACTION_UP, -threshold / 2, y)
        }
        assertTrue(accepted.isEmpty())
        gesture(5) { y ->
            send(MotionEvent.ACTION_MOVE, -threshold, y)
            var kneeWidth = 0
            var initialRight = 0
            var chipBackground: Drawable? = null
            main {
                val row = row(list, records[5])
                val chip = row.getChildAt(0) as ImageView
                assertEquals(View.VISIBLE, chip.visibility)
                assertEquals(0, row.button.left)
                assertEquals(row.width, chip.right)
                assertEquals(ImageView.ScaleType.CENTER_INSIDE, chip.scaleType)
                kneeWidth = chip.width
                initialRight = row.button.right
                chipBackground = chip.background
            }
            val farther = threshold + 40f * density
            send(MotionEvent.ACTION_MOVE, -farther, y)
            main {
                val row = row(list, records[5])
                val chip = row.getChildAt(0) as ImageView
                assertTrue(chip.width > kneeWidth)
                assertTrue(chip.width < kneeWidth + 14f * density)
                assertTrue(row.button.right < initialRight)
                assertSame(chipBackground, chip.background)
            }
            send(MotionEvent.ACTION_UP, -farther, y)
        }
        assertEquals(listOf(setOf(records[5])), accepted)
        main {
            selection = selection - records[5]
            list.setSelection(selection)
        }
        gesture(1) { y ->
            val dx = threshold + 8f * density
            send(MotionEvent.ACTION_MOVE, dx, y)
            main {
                val row = row(list, records[1])
                assertEquals(0, row.getChildAt(0).left)
                assertTrue(row.button.left > 0)
                assertEquals(row.width, row.button.right)
            }
            send(MotionEvent.ACTION_UP, dx, y)
        }
        assertEquals(records.subList(1, 6).toSet(), accepted.last())
        assertEquals(records.subList(0, 6).toSet(), selection)
        main {
            selection = emptySet()
            list.setSelection(selection)
        }
        gesture(6) { y ->
            send(MotionEvent.ACTION_MOVE, threshold + 8f * density, y)
            main { assertChipCorners(row(list, records[6]), outerTop = false, outerBottom = true) }
            send(MotionEvent.ACTION_UP, threshold + 8f * density, y)
        }
        assertEquals(setOf(records[6]), accepted.last())
        gesture(0) { y ->
            send(MotionEvent.ACTION_MOVE, -threshold - 8f * density, y)
            main { assertChipCorners(row(list, records[0]), outerTop = true, outerBottom = false) }
            send(MotionEvent.ACTION_UP, -threshold - 8f * density, y)
        }
        assertEquals(records.toSet(), accepted.last())
    }

    @Test fun horizontalGesturesDeselectSingletonAndSelectedRanges() {
        val records = (0 until 7).map { ArchiveRecordList.Row("Fixture $it") { fail("Drag became a tap") } }
        lateinit var list: ArchiveRecordList
        var selection = setOf(records[3])
        main {
            list = ArchiveRecordList(activity, "Records") { range, select ->
                selection = if (select) selection + range else selection - range
                list.setSelection(selection)
            }
            list.setRows(records)
            list.setSelection(selection)
        }
        mount(list, width = 800, height = 1450)
        fun selectionOf(vararg indices: Int) = indices.map(records::get).toSet()
        fun swipe(index: Int, direction: Int) {
            var y = 0f
            main { y = row(list, records[index]).let { it.top + it.height / 2f } }
            val from = 380f
            val distance = ViewConfiguration.get(activity).scaledTouchSlop + 64f * activity.resources.displayMetrics.density
            val to = from + direction * distance
            touch(list, MotionEvent.ACTION_DOWN, Triple(0, from, y))
            touch(list, MotionEvent.ACTION_MOVE, Triple(0, to, y))
            touch(list, MotionEvent.ACTION_UP, Triple(0, to, y))
        }
        fun assertFill(index: Int, expected: Int) {
            val deadline = SystemClock.uptimeMillis() + 1500L
            var actual: Int
            do {
                actual = 0
                main { actual = row(list, records[index]).button.fillColor }
                if (actual == expected) return
                SystemClock.sleep(16)
            } while (SystemClock.uptimeMillis() < deadline)
            assertEquals(expected, actual)
        }

        swipe(3, 1)
        assertTrue(selection.isEmpty())
        assertFill(3, SettingsComponentColors.CONTENT)
        main {
            selection = selectionOf(0, 1, 3, 4, 6)
            list.setSelection(selection)
        }
        swipe(4, -1)
        assertEquals(selectionOf(0, 1, 3, 4, 6), selection)
        assertFill(4, SettingsComponentColors.ARCHIVE_DESELECT_ANCHOR_FILL)
        swipe(4, 1)
        assertEquals(selectionOf(0, 1, 3, 4, 6), selection)
        swipe(1, 1)
        assertEquals(selectionOf(0, 6), selection)
        assertFill(4, SettingsComponentColors.CONTENT)

        swipe(2, 1)
        swipe(6, 1)
        assertEquals(selectionOf(0, 2, 6), selection)
        assertFill(6, SettingsComponentColors.ARCHIVE_DESELECT_ANCHOR_FILL)
        swipe(3, -1)
        assertFill(6, SettingsComponentColors.ARCHIVE_SELECTED_FILL)
        swipe(5, 1)
        assertEquals(selectionOf(0, 2, 3, 4, 5, 6), selection)
    }

    private fun assertChipCorners(row: ArchiveRecordRow, outerTop: Boolean, outerBottom: Boolean) {
        val chip = row.getChildAt(0)
        val inner = dp(activity, SettingsGroupDimensions.INNER_RADIUS_DP).toFloat()
        val outer = dp(activity, SettingsGroupDimensions.OUTER_RADIUS_DP).toFloat()
        val expected = ContinuousCornerDrawable(SettingsComponentColors.CONTENT, 0f).apply {
            setRadii(if (outerTop) outer else inner, if (outerBottom) outer else inner)
        }
        fun render(drawable: Drawable): Bitmap = Bitmap.createBitmap(chip.width, chip.height, Bitmap.Config.ARGB_8888)
            .also { bitmap ->
                drawable.setBounds(0, 0, bitmap.width, bitmap.height)
                drawable.draw(Canvas(bitmap))
            }
        assertTrue(render(expected).sameAs(render(chip.background)))
    }

    private fun row(list: ArchiveRecordList, record: ArchiveRecordList.Row): ArchiveRecordRow {
        val rows = ((list.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup)
        return (0 until rows.childCount).map { rows.getChildAt(it) as ArchiveRecordRow }
            .single { it.button.contentDescription == record.label }
    }
}
