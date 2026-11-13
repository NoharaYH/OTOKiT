package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.PressSurface
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PressFrontColumnTest : UiComponentTestHost() {
    private val drawn = mutableListOf<String>()
    private fun surface(name: String) = object : PressSurface(activity) {
        override fun draw(canvas: Canvas) { drawn += name; super.draw(canvas) }
    }

    private fun assertOrder(column: PressFrontColumn, expected: List<String>) = main {
        val bitmap = Bitmap.createBitmap(column.width, column.height, Bitmap.Config.ARGB_8888)
        drawn.clear()
        column.draw(Canvas(bitmap))
        assertEquals(expected, drawn.toList())
        bitmap.recycle()
    }

    @Test fun nestedPressRaisesWholeRowAndRemovingRowUnbindsIt() {
        lateinit var column: PressFrontColumn
        lateinit var first: PressSurface
        lateinit var wrapper: FrameLayout
        main {
            first = surface("first")
            wrapper = FrameLayout(activity).apply { addView(first, FrameLayout.LayoutParams(-1, -1)) }
            column = PressFrontColumn(activity).apply {
                addView(wrapper, LinearLayout.LayoutParams(-1, 120))
                addView(surface("second"), LinearLayout.LayoutParams(-1, 120))
                bind(first)
                bind(first) // 重复注册不应产生第二份监听。
            }
        }
        mount(column, 300, 240)
        assertOrder(column, listOf("first", "second"))
        touch(first, MotionEvent.ACTION_DOWN, Triple(0, 60f, 60f))
        assertOrder(column, listOf("second", "first"))
        main { assertEquals(wrapper, column.getChildAt(0)) }
        touch(first, MotionEvent.ACTION_CANCEL, Triple(0, 60f, 60f))
        main { column.removeView(wrapper); column.addView(wrapper, 0, LinearLayout.LayoutParams(-1, 120)) }
        instrumentation.waitForIdleSync()
        touch(first, MotionEvent.ACTION_DOWN, Triple(0, 60f, 60f))
        assertOrder(column, listOf("first", "second"))
        touch(first, MotionEvent.ACTION_CANCEL, Triple(0, 60f, 60f))
        main { column.bind(first) }
        touch(first, MotionEvent.ACTION_DOWN, Triple(0, 60f, 60f))
        assertOrder(column, listOf("second", "first"))
        touch(first, MotionEvent.ACTION_CANCEL, Triple(0, 60f, 60f))
    }

    @Test fun directChildrenStillAutoBindAndOldDisposerCannotRemoveNewBinding() {
        lateinit var column: PressFrontColumn
        lateinit var first: PressSurface
        main {
            first = surface("first")
            column = PressFrontColumn(activity).apply {
                addView(first, LinearLayout.LayoutParams(-1, 120))
                addView(surface("second"), LinearLayout.LayoutParams(-1, 120))
                val old = bind(first)
                old()
                bind(first)
                old()
            }
        }
        mount(column, 300, 240)
        touch(first, MotionEvent.ACTION_DOWN, Triple(0, 60f, 60f))
        assertOrder(column, listOf("second", "first"))
        touch(first, MotionEvent.ACTION_CANCEL, Triple(0, 60f, 60f))
    }
}
