package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.ContentScrollView
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsPageScrollView
import org.junit.Assert.*
import org.junit.Test

class ContentScrollViewTest : UiComponentTestHost() {
    @Test fun settingsDrawToTheWindowBottomButTheirLastControlStopsAboveTheGestureInset() {
        main { host.visibility = View.GONE }
        val menuLabel = activity.getString(R.string.title_open_navigation_menu)
        val settingsLabel = activity.getString(R.string.nav_settings)
        // Compact 从抽屉进入；宽屏 Rail 直接提供设置入口，没有打开抽屉的按钮。
        val floatingMenu = activity.getString(R.string.floating_menu)
        if (clickVisibleAction(menuLabel, floatingMenu, settingsLabel) != settingsLabel) clickVisibleAction(settingsLabel)
        clickVisibleAction(activity.getString(R.string.settings_tile_sync))
        lateinit var scroll: ContentScrollView
        awaitUi {
            val row = activity.window.decorView.allViews.filterIsInstance<GroupedSwitchRow>().singleOrNull {
                it.contentDescription == activity.getString(R.string.settings_sync_logs_save) && settled(it)
            } ?: return@awaitUi false
            scroll = generateSequence(row.parent as? View) { it.parent as? View }
                .filterIsInstance<ContentScrollView>().first()
            settled(scroll)
        }
        var bottom = 0
        main {
            // A focus scroll can stop short of the native touch range when bottom padding is present.
            bottom = (scroll.getChildAt(0).height - scroll.height + scroll.paddingTop + scroll.paddingBottom).coerceAtLeast(0)
            scroll.scrollTo(0, bottom)
            assertEquals(bottom, scroll.scrollY)
            assertEdgePull(scroll, direction = -1)
        }
        awaitUi { scroll.getChildAt(0).translationY == 0f }
        main {
            assertEquals(bottom, scroll.scrollY)
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!
            val bottomSafe = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
            )
            val windowBottom = screenBottom(decor)
            assertEquals("滚动绘制视口必须延伸到窗口底边", windowBottom, screenBottom(scroll))
            val lastAction = scroll.allViews.filterIsInstance<SettingsButton>().single {
                it.contentDescription == activity.getString(R.string.settings_proxy_urls) && it.isShown
            }
            val lastBottom = screenBottom(lastAction)
            assertTrue("滚动到底后末项必须完整位于手势安全区上方", lastBottom <= windowBottom - bottomSafe)
            assertFalse(scroll.canScrollVertically(1))
            assertFalse(scroll.isVerticalScrollBarEnabled)
            assertFalse(scroll.isHorizontalScrollBarEnabled)
            scroll.fullScroll(View.FOCUS_UP)
        }
        awaitUi { !scroll.canScrollVertically(-1) }
        main { assertEdgePull(scroll, direction = 1) }
        awaitUi { scroll.getChildAt(0).translationY == 0f }
        main { assertEquals(0, scroll.scrollY) }
    }

    @Test fun shortSettingsPageKeepsChildTapsAndReturnsAfterVerticalPull() {
        lateinit var scroll: SettingsPageScrollView
        var clicks = 0
        main {
            scroll = SettingsPageScrollView(activity).apply {
                addView(View(activity).apply {
                    minimumHeight = 120
                    setOnClickListener { clicks++ }
                }, FrameLayout.LayoutParams(-1, -2))
            }
        }
        mount(scroll, 300, 360)
        main {
            assertFalse(scroll.canScrollVertically(1))
            val time = SystemClock.uptimeMillis()
            sendTouch(scroll, time, 0, MotionEvent.ACTION_DOWN, 40f)
            sendTouch(scroll, time, 16, MotionEvent.ACTION_UP, 40f)
        }
        awaitUi { clicks == 1 }
        main { assertEdgePull(scroll, direction = 1, startY = 40f) }
        awaitUi { scroll.getChildAt(0).translationY == 0f }
        main {
            assertEquals(1, clicks)
            assertEquals(0, scroll.scrollY)
        }
    }

    @Test fun safePaddingPreservesFullDrawingViewportAndKeepsTheLastItemAboveItsEnd() {
        lateinit var scroll: ContentScrollView
        lateinit var body: View
        main {
            // ScrollView 以 UNSPECIFIED 测子内容，自然高度不能只写在 LayoutParams 中。
            body = View(activity).apply { minimumHeight = 900; setBackgroundColor(Color.GREEN) }
            scroll = ContentScrollView(activity).apply {
                setBackgroundColor(Color.WHITE)
                setPadding(7, 9, 11, 12)
                isSmoothScrollingEnabled = false
                addView(body, FrameLayout.LayoutParams(-1, -2))
                setSafeInsets(40, 95)
            }
        }
        mount(scroll, 300, 240)
        main {
            assertEquals(300, scroll.width)
            assertEquals(240, scroll.height)
            assertEquals(107, scroll.paddingBottom)
            assertEquals(49, body.top)
            assertFalse(scroll.clipToPadding)
            assertFalse(scroll.isVerticalScrollBarEnabled)
            assertFalse(scroll.isHorizontalScrollBarEnabled)
            assertEquals(900, body.height)
            scroll.scrollTo(0, 200)
            assertEquals(200, scroll.scrollY)
            val frame = Bitmap.createBitmap(scroll.width, scroll.height, Bitmap.Config.ARGB_8888)
            try {
                fun pixelAt(y: Int): Int {
                    frame.eraseColor(Color.TRANSPARENT)
                    // View 的父级软件绘制会预先应用 -scroll 偏移；public draw 不替调用者做这一步。
                    val canvas = Canvas(frame).apply { translate(-scroll.scrollX.toFloat(), -scroll.scrollY.toFloat()) }
                    scroll.draw(canvas)
                    return frame.getPixel(scroll.width / 2, y)
                }
                // 滚动经过底部安全区时仍绘制内容，不在安全区顶部硬切一刀。
                assertEquals(Color.GREEN, pixelAt(4))
                assertEquals(Color.GREEN, pixelAt(scroll.height - 4))
                scroll.clipToPadding = true
                assertEquals(Color.WHITE, pixelAt(4))
                assertEquals(Color.WHITE, pixelAt(scroll.height - 4))
                scroll.clipToPadding = false
            } finally { frame.recycle() }
            scroll.fullScroll(View.FOCUS_DOWN)
            assertLastItemAbovePadding(scroll, body)
        }
        for (inset in listOf(47, 0, 95)) {
            main { scroll.setSafeInsets(0, inset); scroll.setSafeInsets(0, inset) }
            instrumentation.waitForIdleSync()
            main {
                assertEquals(7, scroll.paddingLeft)
                assertEquals(9, scroll.paddingTop)
                assertEquals(11, scroll.paddingRight)
                assertEquals(12 + inset, scroll.paddingBottom)
                assertEquals(240, scroll.height)
                scroll.fullScroll(View.FOCUS_DOWN)
                assertLastItemAbovePadding(scroll, body)
            }
        }
    }

    private fun assertLastItemAbovePadding(scroll: ContentScrollView, body: View) {
        val viewport = IntArray(2)
        val content = IntArray(2)
        scroll.getLocationOnScreen(viewport)
        body.getLocationOnScreen(content)
        assertEquals(viewport[1] + scroll.height - scroll.paddingBottom, content[1] + body.height)
    }

    private fun assertEdgePull(scroll: ContentScrollView, direction: Int, startY: Float = scroll.height / 2f) {
        val time = SystemClock.uptimeMillis()
        val slop = ViewConfiguration.get(scroll.context).scaledTouchSlop
        val scrollY = scroll.scrollY
        val viewportBottom = screenBottom(scroll)
        val bottomPadding = scroll.paddingBottom
        sendTouch(scroll, time, 0, MotionEvent.ACTION_DOWN, startY)
        sendTouch(scroll, time, 16, MotionEvent.ACTION_MOVE, startY + direction * (slop + 8))
        val endY = startY + direction * (slop + 48)
        sendTouch(scroll, time, 32, MotionEvent.ACTION_MOVE, endY)
        assertTrue(scroll.getChildAt(0).translationY * direction > 0f)
        assertEquals(scrollY, scroll.scrollY)
        assertEquals(viewportBottom, screenBottom(scroll))
        assertEquals(bottomPadding, scroll.paddingBottom)
        sendTouch(scroll, time, 48, MotionEvent.ACTION_UP, endY)
    }

    private fun sendTouch(scroll: ContentScrollView, time: Long, elapsed: Long, action: Int, y: Float) {
        val event = MotionEvent.obtain(time, time + elapsed, action, scroll.width / 2f, y, 0)
        try { scroll.dispatchTouchEvent(event) } finally { event.recycle() }
    }

    private fun clickVisibleAction(vararg labels: String): String {
        lateinit var target: View
        awaitUi {
            target = activity.window.decorView.allViews.firstOrNull {
                labels.any { label -> it.contentDescription == label } && it.isClickable && it.isEnabled &&
                    settled(it) && it.getGlobalVisibleRect(Rect())
            } ?: return@awaitUi false
            true
        }
        var clickedLabel = ""
        main {
            clickedLabel = target.contentDescription.toString()
            target.performClick()
        }
        return clickedLabel
    }

    private fun settled(view: View): Boolean = view.isShown && view.isLaidOut &&
        generateSequence(view) { it.parent as? View }.all {
            it.alpha == 1f && it.translationX == 0f && it.translationY == 0f && it.scaleX == 1f && it.scaleY == 1f
        }

    private fun screenBottom(view: View): Int = IntArray(2).let {
        view.getLocationOnScreen(it)
        it[1] + view.height
    }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 4000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("实际页面未收敛到待验证的显示状态")
    }
}
