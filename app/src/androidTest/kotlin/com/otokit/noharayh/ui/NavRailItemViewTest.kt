package com.otokit.noharayh.ui

import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.common.NavRailItemView
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavRailItemViewTest : UiComponentTestHost() {
    private lateinit var item: NavRailItemView
    private var clicks = 0
    private val primary = Triple(0, 60f, 60f)
    private val secondary = Triple(1, 100f, 60f)

    @Before fun createItem() {
        main { item = NavRailItemView(activity).apply { setOnClickListener { clicks++ } } }
        mount(item)
    }

    @Test fun tapClicksExactlyOnce() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(1, clicks) }
    }

    @Test fun disabledItemDoesNotClick() {
        main { item.isEnabled = false }
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(0, clicks) }
    }

    @Test fun primaryPointerLeavingCancelsRemainingPointer() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), primary, secondary)
        touch(item, MotionEvent.ACTION_POINTER_UP, primary, secondary)
        touch(item, MotionEvent.ACTION_UP, secondary)
        main { assertEquals(0, clicks) }
    }

    @Test fun secondaryPointerLeavingKeepsPrimaryClick() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), primary, secondary)
        touch(item, MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), primary, secondary)
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(1, clicks) }
    }

    @Test fun movingOutsideThenBackDoesNotClick() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_MOVE, Triple(0, 320f, 60f))
        touch(item, MotionEvent.ACTION_MOVE, primary)
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(0, clicks) }
    }

    @Test fun disablingAndEnablingDuringPressCancelsClick() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        main { item.isEnabled = false; item.isEnabled = true }
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(0, clicks) }
    }

    @Test fun cancelDoesNotClickAndNextTapStillWorks() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_CANCEL, primary)
        touch(item, MotionEvent.ACTION_DOWN, primary)
        touch(item, MotionEvent.ACTION_UP, primary)
        main { assertEquals(1, clicks) }
    }

    @Test fun detachingWhilePressedResetsScale() {
        touch(item, MotionEvent.ACTION_DOWN, primary)
        main {
            host.removeView(item)
            assertEquals(1f, item.scaleX, 0f)
            assertEquals(1f, item.scaleY, 0f)
        }
    }
}
