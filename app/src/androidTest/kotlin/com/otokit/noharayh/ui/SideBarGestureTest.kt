package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.core.view.children
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.chrome.SideBar
import com.otokit.noharayh.ui.chrome.SideBarHost
import com.otokit.noharayh.ui.window.PixelRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SideBarGestureTest : UiComponentTestHost() {
    @Test fun drawerFollowsUntilOneThirdThenOpensWhileFingerIsDown() {
        lateinit var drawer: SideBarHost
        lateinit var panel: SideBar
        main {
            drawer = SideBarHost(activity)
            drawer.addView(View(activity).apply { isClickable = true }, 0)
            drawer.setMetrics(PixelRect(0, 0, 400, 400), 300)
            drawer.setDragEnabled(true)
            panel = drawer.children.filterIsInstance<SideBar>().single()
        }
        mount(drawer, 400, 400)
        var down = 0L
        fun move(action: Int, x: Int) = main {
            val now = SystemClock.uptimeMillis()
            if (action == MotionEvent.ACTION_DOWN) down = now
            val event = MotionEvent.obtain(down, now, action, x.toFloat(), 150f, 0)
            try { drawer.dispatchTouchEvent(event) } finally { event.recycle() }
        }
        move(MotionEvent.ACTION_DOWN, 10)
        move(MotionEvent.ACTION_MOVE, 55)
        move(MotionEvent.ACTION_MOVE, 100)
        main { assertEquals(90, panel.right) }
        move(MotionEvent.ACTION_MOVE, 75)
        main { assertEquals(65, panel.right) }
        move(MotionEvent.ACTION_MOVE, 112)
        move(MotionEvent.ACTION_MOVE, 25)
        val deadline = SystemClock.uptimeMillis() + 1500
        var right = 0
        do {
            main { right = panel.right }
            if (right == 300) break
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        assertEquals(300, right)
        move(MotionEvent.ACTION_UP, 25)

        move(MotionEvent.ACTION_DOWN, 270)
        move(MotionEvent.ACTION_MOVE, 220)
        move(MotionEvent.ACTION_MOVE, 60)
        main { assertEquals(90, panel.right) }
        // 从收起手势反向猛拉，一次 MOVE 就跨过三分之一。
        move(MotionEvent.ACTION_MOVE, 360)
        main { assertTrue("reversing the drag must hand off before full width", panel.right in 100 until 300) }
        move(MotionEvent.ACTION_UP, 360)
        SystemClock.sleep(400)
        main { assertEquals(300, panel.right) }

        move(MotionEvent.ACTION_DOWN, 270)
        move(MotionEvent.ACTION_MOVE, 220)
        move(MotionEvent.ACTION_MOVE, 0)
        move(MotionEvent.ACTION_UP, 0)
    }
}
