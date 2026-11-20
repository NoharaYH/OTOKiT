package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.common.EdgePullScrollView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgePullScrollViewTest : UiComponentTestHost() {
    @Test fun pullingNestedLogAtTheEndDoesNotScrollItsOuterPanel() {
        lateinit var outer: ScrollView
        lateinit var log: EdgePullScrollView
        lateinit var body: View
        main {
            body = View(activity).apply { minimumHeight = 1000 }
            log = EdgePullScrollView(activity, 144f).apply {
                isNestedScrollingEnabled = true
                addView(body, FrameLayout.LayoutParams(-1, -2))
            }
            outer = ScrollView(activity).apply {
                addView(LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(log, LinearLayout.LayoutParams(-1, 360))
                    addView(View(activity), LinearLayout.LayoutParams(-1, 900))
                }, FrameLayout.LayoutParams(-1, -2))
            }
        }
        mount(outer, width = 600, height = 600)
        main {
            assertTrue(outer.canScrollVertically(1))
            val end = body.height - log.height
            assertTrue(end > 0)
            log.scrollTo(0, end)
            val now = SystemClock.uptimeMillis()
            fun send(action: Int, elapsed: Long, y: Float) {
                val event = MotionEvent.obtain(now, now + elapsed, action, 300f, y, 0)
                try { outer.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 0, 280f)
            assertTrue(log.hasNestedScrollingParent())
            send(MotionEvent.ACTION_MOVE, 16, 280f - 2 * ViewConfiguration.get(activity).scaledTouchSlop)
            send(MotionEvent.ACTION_MOVE, 32, 150f)

            assertTrue("translation=${body.translationY}, outer=${outer.scrollY}, inner=${log.scrollY}/$end, " +
                "touching=${log.isTouchActive}, nested=${log.hasNestedScrollingParent()}", body.translationY < 0f)
            assertEquals(end, log.scrollY)
            assertEquals(0, outer.scrollY)
            send(MotionEvent.ACTION_UP, 48, 120f)
        }
        SystemClock.sleep(300)
        main { assertEquals(0, outer.scrollY) }
    }
}
