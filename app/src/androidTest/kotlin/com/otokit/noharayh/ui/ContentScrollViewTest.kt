package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.ContentScrollView
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import org.junit.Assert.*
import org.junit.Test

class ContentScrollViewTest : UiComponentTestHost() {
    @Test fun settingsDrawToTheWindowBottomButTheirLastControlStopsAboveTheGestureInset() {
        main { host.visibility = View.GONE }
        clickVisibleAction("打开导航菜单")
        clickVisibleAction("设置")
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
        main { scroll.fullScroll(View.FOCUS_DOWN) }
        awaitUi { !scroll.canScrollVertically(1) }
        main {
            val decor = activity.window.decorView
            val insets = ViewCompat.getRootWindowInsets(decor)!!
            val bottomSafe = maxOf(
                insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
                insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
            )
            val windowBottom = screenBottom(decor)
            assertEquals("滚动绘制视口必须延伸到窗口底边", windowBottom, screenBottom(scroll))
            val lastBottom = scroll.allViews.filterIsInstance<GroupedSwitchRow>().filter { it.isShown }
                .maxOf(::screenBottom)
            assertTrue("滚动到底后末项必须完整位于手势安全区上方", lastBottom <= windowBottom - bottomSafe)
            assertFalse(scroll.canScrollVertically(1))
            assertFalse(scroll.isVerticalScrollBarEnabled)
            assertFalse(scroll.isHorizontalScrollBarEnabled)
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
                setBottomSafeInset(95)
            }
        }
        mount(scroll, 300, 240)
        main {
            assertEquals(300, scroll.width)
            assertEquals(240, scroll.height)
            assertEquals(107, scroll.paddingBottom)
            assertFalse(scroll.clipToPadding)
            assertFalse(scroll.isVerticalScrollBarEnabled)
            assertFalse(scroll.isHorizontalScrollBarEnabled)
            assertEquals(900, body.height)
            scroll.scrollTo(0, 200)
            assertEquals(200, scroll.scrollY)
            val frame = Bitmap.createBitmap(scroll.width, scroll.height, Bitmap.Config.ARGB_8888)
            try {
                fun bottomPixel(): Int {
                    frame.eraseColor(Color.TRANSPARENT)
                    // View 的父级软件绘制会预先应用 -scroll 偏移；public draw 不替调用者做这一步。
                    val canvas = Canvas(frame).apply { translate(-scroll.scrollX.toFloat(), -scroll.scrollY.toFloat()) }
                    scroll.draw(canvas)
                    return frame.getPixel(scroll.width / 2, scroll.height - 4)
                }
                // 滚动经过底部安全区时仍绘制内容，不在安全区顶部硬切一刀。
                assertEquals(Color.GREEN, bottomPixel())
                scroll.clipToPadding = true
                assertEquals(Color.WHITE, bottomPixel()) // 对照证明该断言能检出安全区顶部硬裁。
                scroll.clipToPadding = false
            } finally { frame.recycle() }
            scroll.fullScroll(View.FOCUS_DOWN)
            assertLastItemAbovePadding(scroll, body)
        }
        for (inset in listOf(47, 0, 95)) {
            main { scroll.setBottomSafeInset(inset); scroll.setBottomSafeInset(inset) }
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

    private fun clickVisibleAction(label: String) {
        lateinit var target: View
        awaitUi {
            target = activity.window.decorView.allViews.firstOrNull {
                it.contentDescription == label && it.isClickable && it.isEnabled && settled(it)
            } ?: return@awaitUi false
            true
        }
        main { target.performClick() }
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
