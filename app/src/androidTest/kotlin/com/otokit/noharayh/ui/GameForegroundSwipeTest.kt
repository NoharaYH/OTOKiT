package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.viewpager2.widget.ViewPager2
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.pages.GameSwipeLayout
import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GameForegroundSwipeTest : UiComponentTestHost() {
    @Test fun gameDragAndEdgePullLeaveTheBackgroundOnTheConfirmedGame() {
        val foreground = activity.findViewById<GameSwipeLayout>(R.id.content_host)
        val background = activity.findViewById<ViewGroup>(R.id.background_host)
        lateinit var pager: ViewPager2
        main { pager = (background.getChildAt(0) as ViewGroup).getChildAt(0) as ViewPager2 }
        await { pager.isShown && pager.width > 0 && (pager.getChildAt(0) as ViewGroup).childCount == 2 }

        main {
            assertFalse(pager.isUserInputEnabled)
            val game = foreground.selectedGame()
            val currentItem = pager.currentItem
            val pages = pager.getChildAt(0) as ViewGroup
            fun backgroundFrame() = (0 until pages.childCount).map { index ->
                pages.getChildAt(index).let { Triple(it.left, it.translationX, it.alpha) }
            }
            val before = backgroundFrame()
            val direction = if (game == GameLine.Mai) 1f else -1f
            val width = foreground.width.toFloat()
            val x = width / 2
            val y = foreground.height / 3f
            val downTime = SystemClock.uptimeMillis()
            var time = downTime
            fun send(action: Int, delta: Float) {
                val event = MotionEvent.obtain(downTime, time, action, x + delta, y, 0)
                time += 16
                try { foreground.dispatchTouchEvent(event) } finally { event.recycle() }
            }
            send(MotionEvent.ACTION_DOWN, 0f)
            send(MotionEvent.ACTION_MOVE, -direction * width / 4)
            assertEquals(GamePhase.Dragging, foreground.phase)
            assertTrue(foreground.progress > 0f && foreground.progress < 1f)
            assertEquals(before, backgroundFrame())

            send(MotionEvent.ACTION_MOVE, direction * width / 10)
            val shortPull = abs(foreground.edgePull)
            send(MotionEvent.ACTION_MOVE, direction * width / 5)
            val longPull = abs(foreground.edgePull)
            assertTrue(longPull > shortPull && longPull - shortPull < shortPull)
            assertEquals(game, foreground.selectedGame())
            assertEquals(currentItem, pager.currentItem)
            assertEquals(before, backgroundFrame())
            send(MotionEvent.ACTION_CANCEL, direction * width / 5)
        }
        await { foreground.phase == GamePhase.Idle && foreground.edgePull == 0f }
    }

    private fun await(condition: () -> Boolean) {
        val timeout = SystemClock.uptimeMillis() + 2500L
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < timeout)
        fail("Foreground swipe did not settle")
    }
}
