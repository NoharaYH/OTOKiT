package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.os.SystemClock
import android.view.ViewGroup
import com.otokit.noharayh.ui.kit.settings.MaterkitMenuPreview
import com.otokit.noharayh.ui.kit.settings.ThemeDropdownMenu
import com.otokit.noharayh.ui.kit.settings.ThemeMenuPreview
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeOption
import org.junit.Assert.*
import org.junit.Test

class MaterkitPreviewRenderingTest : UiComponentTestHost() {
    @Test fun nativeLayersRecolorWithoutCumulativeLossAndSelectedSceneIsGrayscale() {
        val green = 0xFF006E24.toInt()
        val pink = 0xFFB00068.toInt()
        val preview = ThemeMenuPreview(green, 1f, MaterkitMenuPreview(activity.assets, green))
        preview.setTextExtent(180f, 900f)
        preview.render(1f, false)
        fun render(width: Int, progress: Float): Bitmap {
            preview.motion(progress)
            val frame = Bitmap.createBitmap(width, 44, Bitmap.Config.ARGB_8888)
            val bounds = RectF(0f, 0f, width.toFloat(), 44f)
            val path = Path().apply { addRect(bounds, Path.Direction.CW) }
            Canvas(frame).apply { clipPath(path); preview.draw(this, bounds, path) }
            return frame
        }
        for (width in listOf(300, 900, 1916)) {
            val before = render(width, 1f)
            assertEquals(255, Color.alpha(before.getPixel(width - 1, 0)))
            assertEquals(255, Color.alpha(before.getPixel(width - 1, 43)))
            preview.setPrimaryColor(pink)
            val changed = render(width, 1f)
            assertFalse(before.sameAs(changed))
            preview.setPrimaryColor(green)
            val restored = render(width, 1f)
            assertTrue(before.sameAs(restored))
            preview.render(1f, true)
            val selected = render(width, 1f)
            for (x in width / 2 until width step 7) for (y in 3..40 step 5) {
                val pixel = selected.getPixel(x, y)
                assertTrue(kotlin.math.abs(Color.red(pixel) - Color.green(pixel)) <= 1)
                assertTrue(kotlin.math.abs(Color.red(pixel) - Color.blue(pixel)) <= 1)
            }
            preview.render(1f, false)
            val moving = render(width, 0.35f)
            assertFalse("Fourth-layer entry must produce a different scene before completion", moving.sameAs(before))
            before.recycle(); changed.recycle(); restored.recycle(); selected.recycle(); moving.recycle()
        }
    }

    @Test fun nativeEntranceStopsAndReplaysOnlyAfterCompleteCollapseWithoutBitmapAllocation() {
        lateinit var menu: ThemeDropdownMenu
        val options = listOf(ThemeOptions.placeholder) + (0..16).map { ThemeOption.Placeholder("extra-$it", "Extra $it") }
        main { menu = ThemeDropdownMenu(activity, options, null, 1600, {}, {}) {} }
        mount(menu, 1000, 1800)
        main { menu.setInteractionEnabled(true); menu.setPreviewActive(true) }
        fun settle() {
            var active = true
            val deadline = SystemClock.uptimeMillis() + 4000
            while (active && SystemClock.uptimeMillis() < deadline) {
                main { active = menu.previewHasPendingWork }
                if (active) SystemClock.sleep(20)
            }
            assertFalse(active)
        }
        fun capture(): Bitmap {
            lateinit var frame: Bitmap
            main {
                val row = (menu.getChildAt(0) as ViewGroup).getChildAt(0)
                frame = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                row.draw(Canvas(frame))
            }
            return frame
        }
        settle()
        val first = capture()
        main {
            assertEquals(0, menu.previewBitmapBytes)
            assertEquals(0, menu.previewImageCount)
            menu.beginScrollbarDrag()
            menu.dragScrollbarTo(0.3f / 12f)
            menu.dragScrollbarTo(0f)
        }
        val partial = capture()
        assertTrue("Partial collapse must not replay a completed entry", first.sameAs(partial))
        partial.recycle()
        settle() // 布局允许发布一次静态重绘；检查稳定后不再连续请求帧。
        main {
            assertFalse(menu.previewHasPendingWork)
            menu.dragScrollbarTo(1f / 12f)
            menu.dragScrollbarTo(0f)
            assertTrue("Complete collapse must rearm the native short clock", menu.previewHasPendingWork)
        }
        settle()
        val replayed = capture()
        assertTrue("Replayed star must stop at the same position and angle", first.sameAs(replayed))
        first.recycle(); replayed.recycle()
        SystemClock.sleep(150)
        main { assertFalse(menu.previewHasPendingWork); menu.endScrollbarDrag() }
    }
}
