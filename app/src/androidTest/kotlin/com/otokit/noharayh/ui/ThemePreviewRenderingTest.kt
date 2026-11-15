package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.*
import com.otokit.noharayh.ui.theme.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreviewRenderingTest : UiComponentTestHost() {
    private fun menu(panel: ThemeSelectorPanel) = (0 until panel.childCount).map(panel::getChildAt).filterIsInstance<ThemeDropdownMenu>().single()
    private fun awaitSettled(panel: ThemeSelectorPanel, expected: Int): ThemeDropdownMenu {
        var found: ThemeDropdownMenu? = null
        val deadline = SystemClock.uptimeMillis() + 6000
        while (SystemClock.uptimeMillis() < deadline) {
            main {
                val current = menu(panel)
                if ((expected < 0 || current.previewImageCount == expected) && !current.previewHasPendingWork &&
                    (current.getChildAt(0) as ViewGroup).let { rows -> (0 until rows.childCount).any { rows.getChildAt(it).isEnabled } }) found = current
            }
            if (found != null) return found!!
            SystemClock.sleep(30)
        }
        error("Preview failed to settle: expected $expected")
    }
    private fun options() = listOf("mai-circle-plus", "mai-festival-plus", "chu-xversex").map { ThemeOption.Html(ThemeRegistry.findById(it)!!) }

    @Test fun actualLayersRenderInColorOrSelectedGrayscaleAndReleaseAfterClosing() {
        lateinit var panel: ThemeSelectorPanel
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Images")) {} }
        mount(panel, 800, 600)
        main { panel.open(options(), 400, "mai-circle-plus") {} }
        val original = awaitSettled(panel, 3)
        main {
            assertTrue(original.previewBitmapBytes in 1..ThemePreviewImages.BYTE_BUDGET)
            android.util.Log.i("ThemePreviewTest", "settled bitmapBytes=${original.previewBitmapBytes}")
            val rows = original.getChildAt(0) as ViewGroup
            for (index in 0..1) {
                val row = rows.getChildAt(index) as ViewGroup
                val bitmap = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                row.draw(Canvas(bitmap))
                var colored = 0
                for (y in row.height/4 until row.height*3/4 step 3) for (x in row.width/2 until row.width*9/10 step 3) {
                    val c = bitmap.getPixel(x,y)
                    if (maxOf(Color.red(c),Color.green(c),Color.blue(c))-minOf(Color.red(c),Color.green(c),Color.blue(c)) > 20) colored++
                }
                assertEquals(Color.WHITE, ((row.getChildAt(0) as ViewGroup).getChildAt(0) as TextView).currentTextColor)
                if (index == 0) assertEquals("选中图片必须灰度", 0, colored) else assertTrue("原版图片应保留主题颜色", colored > 20)
                bitmap.recycle()
            }
            assertTrue(panel.beginBackPreview())
            panel.previewBack(0.45f)
            assertFalse(original.previewHasPendingWork)
            panel.cancelBackPreview()
        }
        awaitSettled(panel, 3)
        main { panel.reset() }
        main { assertEquals(0, original.previewBitmapBytes); assertEquals(0, original.previewImageCount); assertFalse(original.previewHasPendingWork) }
    }

    @Test fun missingGroupKeepsUnderpaintAndInputUsableWithoutRetryingEveryFrame() {
        lateinit var panel: ThemeSelectorPanel
        val real = ThemeRegistry.findById("mai-circle-plus")!!
        val missing = real.copy(definition = real.definition.copy(preview = ThemePreviewDescription("theme-preview/missing")))
        var selected = false
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Missing")) {} }
        mount(panel, 600, 400)
        main { panel.open(listOf(ThemeOption.Html(missing)), 300) { selected = true } }
        val original = awaitSettled(panel, 0)
        main {
            val row = (original.getChildAt(0) as ViewGroup).getChildAt(0) as SettingsButton
            assertEquals(ThemeMenuPreviewPalette.fromPrimary(real.definition.colors.primaryColor).underpaint, row.fillColor)
            assertEquals(0, original.previewBitmapBytes)
            row.performClick()
            assertTrue(selected)
        }
    }

    @Test fun closingDuringDecodeCannotPublishToTheNextMenu() {
        lateinit var panel: ThemeSelectorPanel
        lateinit var old: ThemeDropdownMenu
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Cancel")) {} }
        mount(panel, 800, 600)
        main {
            panel.open(options(), 400) {}
            old = menu(panel)
        }
        val deadline = SystemClock.uptimeMillis() + 3000
        var started = false
        while (!started && SystemClock.uptimeMillis() < deadline) {
            main { started = old.previewHasPendingWork }
            if (!started) SystemClock.sleep(10)
        }
        assertTrue("必须在实际切入/解码开始后取消", started)
        main {
            assertTrue(panel.beginBackPreview())
            panel.previewBack(0.3f)
            assertFalse(old.previewHasPendingWork)
            panel.cancelBackPreview()
            panel.reset()
            panel.open(listOf(ThemeOptions.placeholder), 400) {}
        }
        awaitSettled(panel, 0)
        main { assertEquals(0, old.previewImageCount); assertEquals(0, old.previewBitmapBytes); assertFalse(old.previewHasPendingWork) }
    }
    @Test fun everyRegisteredThemeLoadsDuringForwardAndReverseTraversalWithinTheByteBudget() {
        lateinit var panel: ThemeSelectorPanel
        val themes = GameLine.entries.flatMap(ThemeRegistry::themesFor)
        val all = themes.map(ThemeOption::Html)
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "All previews")) {} }
        mount(panel, 800, 1800)
        main { panel.open(all, 1600) {} }
        val current = awaitSettled(panel, -1)
        val seen = HashSet<String>()
        var peak = 0
        val positions = (0..17).toList() + listOf(17, 14, 11, 8, 5, 2, 0)
        for (step in positions) {
            main {
                current.beginScrollbarDrag()
                current.dragScrollbarTo(step / 17f)
                current.endScrollbarDrag()
            }
            awaitSettled(panel, -1)
            main {
                val rows = current.getChildAt(0) as ViewGroup
                val expanded = (0 until rows.childCount).map(rows::getChildAt).filter { it.isEnabled }
                seen.addAll(expanded.map { it.contentDescription.toString() })
                assertTrue("整组图片应就绪", current.previewImageCount >= expanded.size)
                assertTrue("最多六整行加一个交换项", current.previewImageCount <= 7)
                assertTrue(current.previewBitmapBytes <= ThemePreviewImages.BYTE_BUDGET)
                peak = maxOf(peak, current.previewBitmapBytes)
            }
        }
        assertEquals(themes.map { it.displayName }.toSet(), seen)
        android.util.Log.i("ThemePreviewTest", "allThemes=${seen.size} peakBitmapBytes=$peak")
        main { panel.reset(); assertEquals(0, current.previewBitmapBytes); assertFalse(current.previewHasPendingWork) }
    }

}
