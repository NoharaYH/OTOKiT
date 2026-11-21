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
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.PageMotionHost
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgePullScrollViewTest : UiComponentTestHost() {
    @Test fun releasingADeepPullCrossesRestAndSettlesAtTheSameEdge() {
        lateinit var scroll: EdgePullScrollView
        lateinit var body: View
        main {
            body = View(activity).apply { minimumHeight = 2000 }
            scroll = EdgePullScrollView(activity, 100f).apply { addView(body) }
        }
        lateinit var container: FrameLayout
        main {
            container = object : FrameLayout(activity), PageMotionHost {
                override val settleInterpolator = MotionInterpolators.of(MotionCurve.GentleBounce)
            }.apply { addView(scroll) }
        }
        mount(container, 600, 600)
        val crossed = java.util.concurrent.CountDownLatch(1)
        val settled = java.util.concurrent.CountDownLatch(1)
        main {
            val down = SystemClock.uptimeMillis()
            fun send(action: Int, elapsed: Long, y: Float) {
                val event = MotionEvent.obtain(down, down + elapsed, action, 300f, y, 0)
                try { scroll.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 0, 24f)
            send(MotionEvent.ACTION_MOVE, 16, 100f)
            send(MotionEvent.ACTION_MOVE, 32, 550f)
            val held = body.translationY
            assertTrue("deep pull should approach the limit: $held", held in 72f..100f)
            scroll.viewTreeObserver.addOnPreDrawListener {
                if (body.translationY < -1f) crossed.countDown()
                if (crossed.count == 0L && body.translationY == 0f) settled.countDown()
                true
            }
            send(MotionEvent.ACTION_UP, 96, 550f)
            assertEquals("release must start at the held position", held, body.translationY, .01f)
        }
        assertTrue("deep release should visibly rebound", crossed.await(1, java.util.concurrent.TimeUnit.SECONDS))
        assertTrue("spring should settle", settled.await(1, java.util.concurrent.TimeUnit.SECONDS))
        main { assertEquals(0, scroll.scrollY) }
    }

    @Test fun nativeFlingFlowsIntoEdgeReturnAtBothEnds() {
        lateinit var scroll: EdgePullScrollView
        lateinit var body: View
        main {
            body = View(activity).apply { minimumHeight = 2000 }
            scroll = EdgePullScrollView(activity, 100f).apply {
                addView(body, FrameLayout.LayoutParams(-1, -2))
            }
        }
        mount(scroll, 500, 400)
        for (direction in listOf(-1, 1)) {
            val hit = java.util.concurrent.CountDownLatch(1)
            val settled = java.util.concurrent.CountDownLatch(1)
            val listener = android.view.ViewTreeObserver.OnPreDrawListener {
                if (body.translationY * direction < -1f) hit.countDown()
                if (hit.count == 0L && body.translationY == 0f) settled.countDown()
                true
            }
            main {
                val limit = body.height - scroll.height
                scroll.scrollTo(0, if (direction < 0) 120 else limit - 120)
                scroll.viewTreeObserver.addOnPreDrawListener(listener)
                scroll.fling(direction * 3600)
            }
            assertTrue("fling must carry into edge $direction", hit.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertTrue("edge must settle without another touch", settled.await(2, java.util.concurrent.TimeUnit.SECONDS))
            main {
                assertEquals(if (direction < 0) 0 else body.height - scroll.height, scroll.scrollY)
                scroll.viewTreeObserver.removeOnPreDrawListener(listener)
            }
        }
    }

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
