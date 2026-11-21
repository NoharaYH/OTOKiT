package com.otokit.noharayh.ui

import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.core.view.children
import androidx.core.view.allViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.shell.PageScene
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.pages.GamePairLayout
import com.otokit.noharayh.ui.pages.GameSwipeLayout
import com.otokit.noharayh.ui.shell.background.BackgroundHost
import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class GameForegroundSwipeTest : UiComponentTestHost() {
    @Test fun scoreSyncFormsStayPutOnVerticalPullAndSwitchGamesHorizontally() {
        val pager = activity.findViewById<GameSwipeLayout>(R.id.content_host)
        val scene = activity.findViewById<PageScene>(R.id.page_scene)
        await("Score sync forms are mounted") { pager.allViews.any { it is ScrollView && it.isShown && it.height > 0 } }
        for (curve in listOf(MotionCurve.Standard, MotionCurve.GentleBounce, MotionCurve.GentleBounce, MotionCurve.Emphasized)) {
            var crossedHorizontal = false
            lateinit var origin: GameLine
            lateinit var listener: android.view.ViewTreeObserver.OnPreDrawListener
            main {
                origin = pager.selectedGame()
                pager.settleCurve = curve
                scene.settleInterpolator = MotionInterpolators.of(curve)
                val outer = pager.allViews.filterIsInstance<ScrollView>().first { it.isShown }
                val form = outer.getChildAt(0)
                assertEquals(View.OVER_SCROLL_NEVER, outer.overScrollMode)
                outer.scrollTo(0, 0)
                val scrollOrigin = IntArray(2).also { outer.getLocationOnScreen(it) }
                val pagerOrigin = IntArray(2).also { pager.getLocationOnScreen(it) }
                val x = scrollOrigin[0] - pagerOrigin[0] + outer.width * .5f
                val y = scrollOrigin[1] - pagerOrigin[1] + outer.height * .15f
                val now = SystemClock.uptimeMillis()
                fun send(action: Int, elapsed: Long, delta: Float) {
                    val event = MotionEvent.obtain(now, now + elapsed, action, x, y + delta, 0)
                    try { pager.dispatchTouchEvent(event) } finally { event.recycle() }
                }
                listener = android.view.ViewTreeObserver.OnPreDrawListener {
                    val sign = if (origin == GameLine.Mai) -1f else 1f
                    if (pager.edgePull * sign > 1f && pager.phase == GamePhase.Settling) crossedHorizontal = true
                    true
                }
                pager.viewTreeObserver.addOnPreDrawListener(listener)
                send(MotionEvent.ACTION_DOWN, 0, 0f)
                send(MotionEvent.ACTION_MOVE, 16, outer.height * .12f)
                send(MotionEvent.ACTION_MOVE, 32, outer.height * .42f)
                assertEquals(0f, form.translationY, 0f)
                assertEquals(0, outer.scrollY)
                send(MotionEvent.ACTION_UP, 96, outer.height * .42f)
                assertEquals(GamePhase.Idle, pager.phase)
            }
            main {
                val dx = pager.width * if (origin == GameLine.Mai) -.7f else .7f
                val swipe = gesture(pager, pager.width * .5f, pager.height * .3f)
                swipe(MotionEvent.ACTION_DOWN, 0f)
                swipe(MotionEvent.ACTION_MOVE, dx)
                swipe(MotionEvent.ACTION_UP, dx)
            }
            await("Game switches") { pager.phase == GamePhase.Idle && pager.selectedGame() != origin }
            main {
                pager.viewTreeObserver.removeOnPreDrawListener(listener)
                assertEquals("horizontal game rebound for $curve", curve == MotionCurve.GentleBounce, crossedHorizontal)
            }
        }
    }

    @Test fun gameDragAndEdgePullKeepBackgroundPositionsFixed() {
        val foreground = activity.findViewById<GameSwipeLayout>(R.id.content_host)
        val background = activity.findViewById<ViewGroup>(R.id.background_host)
        await("Game swipe and background are ready") {
            val pages = background.getChildAt(0) as FrameLayout
            pages.isShown && pages.width > 0 && pages.childCount > 0 && foreground.createAccessibilityNodeInfo().isScrollable
        }

        lateinit var game: GameLine
        lateinit var positions: Map<View, Pair<Int, Int>>
        main {
            game = foreground.selectedGame()
            positions = pages(background).associateWith(::position)
            val direction = if (game == GameLine.Mai) 1f else -1f
            val width = foreground.width.toFloat()
            val y = foreground.height / 3f
            val cancelDrag = gesture(foreground, width / 2, y)
            cancelDrag(MotionEvent.ACTION_DOWN, 0f)
            val slop = ViewConfiguration.get(foreground.context).scaledTouchSlop
            cancelDrag(MotionEvent.ACTION_MOVE, -direction * (width / 2 + slop))
            assertEquals(GamePhase.Dragging, foreground.phase)
            assertEquals(0.5f, foreground.progress, 0.001f)
            val pair = foreground.children.filterIsInstance<GamePairLayout>().single()
            assertEquals(-pair.width / 2f, pair.getChildAt(0).translationX, 1f)
            assertEquals(pair.width / 2f, pair.getChildAt(1).translationX, 1f)
            pair.children.forEach { panel ->
                assertEquals(View.VISIBLE, panel.visibility)
                assertEquals(1f, panel.scaleX, 0f)
                assertEquals(1f, panel.scaleY, 0f)
                assertEquals(1f, panel.alpha, 0f)
            }
            positions.forEach { (page, before) -> assertEquals(before, position(page)) }

            cancelDrag(MotionEvent.ACTION_MOVE, direction * width / 10)
            val shortPull = abs(foreground.edgePull)
            cancelDrag(MotionEvent.ACTION_MOVE, direction * width / 5)
            val longPull = abs(foreground.edgePull)
            assertTrue(longPull > shortPull && longPull - shortPull < shortPull)
            assertEquals(game, foreground.selectedGame())
            positions.forEach { (page, before) -> assertEquals(before, position(page)) }
            cancelDrag(MotionEvent.ACTION_CANCEL, direction * width / 5)
            Log.i(LOG_TAG, "cancel $game, background positions=${positions.values}, edge pull=$shortPull/$longPull")
        }
        await("Cancelled foreground gesture settles") {
            foreground.phase == GamePhase.Idle && foreground.edgePull == 0f && foreground.selectedGame() == game
        }
        main {
            val width = foreground.width.toFloat()
            val delta = if (game == GameLine.Mai) -width * .7f else width * .7f
            val commitDrag = gesture(foreground, if (delta < 0f) width * .85f else width * .15f, foreground.height / 3f)
            commitDrag(MotionEvent.ACTION_DOWN, 0f)
            commitDrag(MotionEvent.ACTION_MOVE, delta)
            assertEquals(GamePhase.Dragging, foreground.phase)
            positions.forEach { (page, before) -> assertEquals(before, position(page)) }
            commitDrag(MotionEvent.ACTION_UP, delta)
        }
        await("Completed foreground gesture switches games") {
            foreground.phase == GamePhase.Idle && foreground.selectedGame() != game
        }
        main {
            positions.forEach { (page, before) -> assertEquals(before, position(page)) }
            Log.i(LOG_TAG, "commit ${foreground.selectedGame()}, background positions=${positions.values}")
        }
    }

    @Test fun backgroundCrossfadeUsesForegroundProgressAndReusesSources() {
        lateinit var viewport: FrameLayout
        lateinit var background: BackgroundHost
        val none = BackgroundSelection.None
        val placeholder = BackgroundSelection.Placeholder
        main {
            viewport = FrameLayout(activity)
            background = BackgroundHost(viewport).apply {
                attach(GameLine.Mai)
                renderSelection(GameLine.Mai, none, placeholder)
                setResumed(true)
            }
        }
        try {
            mount(viewport, 600, 600)
            main { background.renderGameProgress(.25f) }
            await("Different sources crossfade after the alternate is ready") {
                pages(viewport).map { it.alpha } == listOf(.75f, .25f)
            }
            main {
                val (mai, chu) = pages(viewport)
                val before = listOf(position(mai), position(chu))
                fun expectFrame(maiAlpha: Float, chuAlpha: Float) {
                    val alphas = listOf(mai.alpha, chu.alpha)
                    assertEquals(listOf(maiAlpha, chuAlpha), alphas)
                    assertEquals(before, listOf(position(mai), position(chu)))
                    Log.i(LOG_TAG, "different sources alpha=$alphas positions=$before")
                }
                expectFrame(.75f, .25f)
                background.renderGameProgress(.75f)
                expectFrame(.25f, .75f)
                background.renderGameProgress(0f)
                expectFrame(1f, 0f)
                background.renderGameProgress(1f)
                background.renderSelection(GameLine.Chu, none, placeholder)
                expectFrame(0f, 1f)

                background.renderSelection(GameLine.Chu, placeholder, placeholder)
                background.renderGameProgress(.5f)
                assertSame(chu, pages(viewport).single())
                assertEquals(1f, chu.alpha, 0f)
                background.renderSelection(GameLine.Chu, none, placeholder)
                assertTrue(chu in pages(viewport))
                Log.i(LOG_TAG, "different to shared to different kept the selected renderer ${System.identityHashCode(chu)}")
            }
        } finally {
            main { background.dispose() }
        }
    }

    @Test fun sharedHtmlBackgroundKeepsOnePlayingVideoAcrossGames() {
        val theme = checkNotNull(ThemeRegistry.findById("chu-luminous"))
        val html = BackgroundSelection.Html(theme)
        val sameAsset = BackgroundSelection.Html(theme.copy(id = "test-luminous-alias", gameLine = GameLine.Mai))
        lateinit var viewport: FrameLayout
        lateinit var background: BackgroundHost
        var ready = false
        main {
            viewport = FrameLayout(activity)
            background = BackgroundHost(viewport).apply {
                setOnFirstVisualReady { ready = true }
                attach(GameLine.Mai)
                renderSelection(GameLine.Mai, sameAsset, html)
                setResumed(true)
            }
        }
        try {
            mount(viewport, 600, 600)
            await("Shared LUMINOUS background reaches its first frame", 5000L) { ready }
            lateinit var web: WebView
            main {
                val page = pages(viewport).single() as ViewGroup
                web = page.children.filterIsInstance<WebView>().single()
            }
            awaitVideoTime(web, "video && video.readyState >= 2 && !video.paused", 0.0)
            evaluate(web, "window.testSharedVideo = document.querySelector('video'); window.testSharedVideo.currentTime = 4;")
            val playing = "video && video === window.testSharedVideo && !video.paused && !video.seeking"
            val before = awaitVideoTime(web, playing, 4.0)
            main {
                val page = pages(viewport).single() as ViewGroup
                val beforePosition = position(page)
                background.renderGameProgress(.5f)
                assertEquals(1f, page.alpha, 0f)
                assertEquals(beforePosition, position(page))
                background.renderGameProgress(1f)
                background.renderSelection(GameLine.Chu, sameAsset, html)
                assertSame(page, pages(viewport).single())
                assertSame(web, page.children.filterIsInstance<WebView>().single())
                assertEquals(1f, page.alpha, 0f)
                assertEquals(beforePosition, position(page))
            }
            val after = awaitVideoTime(web, playing, before)
            Log.i(LOG_TAG, "shared LUMINOUS WebView=${System.identityHashCode(web)} video=$before -> $after")

            main {
                val outgoing = pages(viewport).single()
                background.renderSelection(GameLine.Chu, BackgroundSelection.None, BackgroundSelection.None)
                val layers = pages(viewport)
                assertSame(outgoing, layers.first())
                assertEquals(listOf(1f, 0f), layers.map { it.alpha })
            }
            await("Replacement becomes visible and releases the outgoing HTML background") {
                val layers = pages(viewport)
                layers.size == 1 && layers.single().alpha == 1f && web.parent == null
            }
            Log.i(LOG_TAG, "replacement kept outgoing layer until first frame, then released WebView")
        } finally {
            main { background.dispose() }
        }
    }

    private fun gesture(foreground: GameSwipeLayout, x: Float, y: Float): (Int, Float) -> Unit {
        val downTime = SystemClock.uptimeMillis()
        var time = downTime
        return { action, delta ->
            val event = MotionEvent.obtain(downTime, time, action, x + delta, y, 0)
            time += 16
            try { foreground.dispatchTouchEvent(event) } finally { event.recycle() }
        }
    }

    private fun pages(container: ViewGroup) = (container.getChildAt(0) as FrameLayout).children.toList()

    private fun position(page: View): Pair<Int, Int> {
        val location = IntArray(2)
        page.getLocationOnScreen(location)
        return location[0] to location[1]
    }

    private fun evaluate(web: WebView, script: String): String {
        var result = "null"
        val evaluated = CountDownLatch(1)
        main {
            web.evaluateJavascript(script) {
                result = it
                evaluated.countDown()
            }
        }
        assertTrue("WebView responds to its video probe", evaluated.await(5, TimeUnit.SECONDS))
        return result
    }

    private fun awaitVideoTime(web: WebView, whenPlaying: String, after: Double): Double {
        val timeout = SystemClock.uptimeMillis() + 5000L
        val script = "(() => { const video = document.querySelector('video'); return $whenPlaying ? video.currentTime : -1; })()"
        do {
            val time = evaluate(web, script).toDoubleOrNull() ?: -1.0
            if (time > after) return time
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < timeout)
        throw AssertionError("LUMINOUS video did not advance past $after seconds")
    }

    private fun await(message: String, timeoutMs: Long = 2500L, condition: () -> Boolean) {
        val timeout = SystemClock.uptimeMillis() + timeoutMs
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < timeout)
        fail(message)
    }

    private companion object {
        const val LOG_TAG = "GameBackgroundTest"
    }
}
