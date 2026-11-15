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
    private val threeRowBudget: Int get() = (200f * activity.resources.displayMetrics.density).toInt()
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

    @Test fun reversingDuringEntranceEventuallyMatchesAFreshCompletedScene() {
        lateinit var current: ThemeDropdownMenu
        val choices = (0..5).map { ThemeOption.Placeholder("before-$it", "Before $it") } +
            ThemeOption.Html(ThemeRegistry.findById("mai-festival-plus")!!) +
            (0..16).map { ThemeOption.Placeholder("after-$it", "After $it") }
        main { current = ThemeDropdownMenu(activity, choices, null, 1600, {}, {}) {} }
        mount(current, 1000, 1800)
        main { current.setInteractionEnabled(true); current.setPreviewActive(true); current.beginScrollbarDrag(); current.dragScrollbarTo(1f / 18f) }
        val deadline = SystemClock.uptimeMillis() + 6000
        var loaded = false
        while (!loaded && SystemClock.uptimeMillis() < deadline) {
            main { loaded = current.previewImageCount == 1 && !current.previewHasPendingWork }
            if (!loaded) SystemClock.sleep(30)
        }
        assertTrue("Warm actual scene before testing a short reversal", loaded)
        main { current.dragScrollbarTo(0f); current.dragScrollbarTo(1f / 18f) }
        SystemClock.sleep(180)
        main { current.dragScrollbarTo(0.9f / 18f); current.dragScrollbarTo(1f / 18f) }
        SystemClock.sleep(1200)
        fun pixels(): IntArray {
            lateinit var result: IntArray
            main {
                val row = (current.getChildAt(0) as ViewGroup).getChildAt(6)
                assertTrue(row.height > 100)
                val frame = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                row.draw(Canvas(frame))
                val width = row.width / 2 - 8
                val height = row.height - 16
                result = IntArray(width * height)
                frame.getPixels(result, 0, width, row.width / 2, 8, width, height)
                frame.recycle()
            }
            return result
        }
        val reversed = pixels()
        main { current.dragScrollbarTo(0f); current.dragScrollbarTo(1f / 18f) }
        SystemClock.sleep(1200)
        val fresh = pixels()
        val different = reversed.indices.count { reversed[it] != fresh[it] }
        android.util.Log.i("ThemePreviewTest", "reversedEntrance differentPixels=$different sampledPixels=${fresh.size}")
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\nreversedEntrance differentPixels=$different sampledPixels=${fresh.size}\n")
        })
        assertEquals("A short reversal must not strand a translucent displaced character", 0, different)
        main { assertFalse(current.previewHasPendingWork); current.endScrollbarDrag() }
    }

    @Test fun cachedEntrancesHaveTheSameDurationAtTheTopAndBottom() {
        lateinit var current: ThemeDropdownMenu
        val scene = ThemeOption.Html(ThemeRegistry.findById("mai-festival-plus")!!)
        val choices = listOf(scene) + (1..5).map { ThemeOption.Placeholder("middle-$it", "Middle $it") } +
            scene + (0..16).map { ThemeOption.Placeholder("after-$it", "After $it") }
        main { current = ThemeDropdownMenu(activity, choices, null, 1600, {}, {}) {} }
        mount(current, 1000, 1800)
        main { current.setInteractionEnabled(true); current.setPreviewActive(true); current.beginScrollbarDrag() }
        fun settle(): Long {
            val started = SystemClock.uptimeMillis()
            var pending = true
            while (pending && SystemClock.uptimeMillis() - started < 6000) {
                main { pending = current.previewHasPendingWork }
                if (pending) SystemClock.sleep(10)
            }
            assertFalse("Entrance must settle", pending)
            return SystemClock.uptimeMillis() - started
        }
        settle()
        main { current.dragScrollbarTo(1f / 18f) }; settle()
        main { current.dragScrollbarTo(0f) }; settle()
        val bottom = ArrayList<Long>()
        val top = ArrayList<Long>()
        repeat(3) {
            main { current.dragScrollbarTo(1f / 18f) }; bottom.add(settle())
            main { current.dragScrollbarTo(0f) }; top.add(settle())
        }
        instrumentation.sendStatus(0, android.os.Bundle().apply {
            putString("stream", "\ncachedEntrance bottomMs=$bottom topMs=$top\n")
        })
        assertTrue("A single incoming row must not wait for existing rows: bottom=$bottom top=$top",
            kotlin.math.abs(bottom.sorted()[1] - top.sorted()[1]) <= 90)
        main { current.endScrollbarDrag() }
    }

    @Test fun actualLayersKeepTheirColorsWhenSelectedAndReleaseAfterClosing() {
        lateinit var panel: ThemeSelectorPanel
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Images")) {} }
        mount(panel, 800, 600)
        main { panel.open(options(), threeRowBudget, "mai-circle-plus") {} }
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
                assertTrue("选中与未选中的图片都保留主题颜色", colored > 20)
                if (index == 0) {
                    original.renderSelection(null)
                    val unselected = Bitmap.createBitmap(row.width, row.height, Bitmap.Config.ARGB_8888)
                    row.draw(Canvas(unselected))
                    var selectedBrightness = 0L
                    var originalBrightness = 0L
                    for (y in row.height / 3 until row.height * 2 / 3 step 3) {
                        for (x in row.width / 2 until row.width * 3 / 4 step 3) {
                            val a = bitmap.getPixel(x, y)
                            val b = unselected.getPixel(x, y)
                            selectedBrightness += Color.red(a) + Color.green(a) + Color.blue(a)
                            originalBrightness += Color.red(b) + Color.green(b) + Color.blue(b)
                        }
                    }
                    assertTrue("同一幅预览选中后应整体压暗", selectedBrightness < originalBrightness * 0.8)
                    unselected.recycle()
                    original.renderSelection("mai-circle-plus")
                }
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
            panel.open(options(), threeRowBudget) {}
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

    @Test fun liveViewportChangesKeepTheMenuAndResumeAnInterruptedEntranceWithoutOldResults() {
        lateinit var panel: ThemeSelectorPanel
        val all = options() + (1..17).map { ThemeOption.Placeholder("extra-$it", "Extra $it") }
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Resize")) {} }
        mount(panel, 700, 1500)
        main { panel.open(all, 1200) {} }
        val original = awaitSettled(panel, 3)
        val originalHeight = original.contentHeight
        val smallBudget = (90f * activity.resources.displayMetrics.density).toInt()
        main {
            panel.updateHeightBudget(smallBudget)
            assertSame(original, menu(panel))
            assertTrue(original.contentHeight < originalHeight)
            assertTrue(original.contentHeight <= smallBudget + 1)
            panel.layoutParams = panel.layoutParams.apply { width = 900 }
        }
        awaitSettled(panel, 1)
        main {
            assertSame(original, menu(panel))
            assertTrue(original.previewBitmapBytes <= ThemePreviewImages.BYTE_BUDGET)
            panel.updateHeightBudget(1200)
        }
        awaitSettled(panel, 3)
        main { panel.reset(); panel.open(options(), threeRowBudget) {} }
        val entering = menuOnMain(panel)
        val deadline = SystemClock.uptimeMillis() + 3000
        var started = false
        while (!started && SystemClock.uptimeMillis() < deadline) {
            main { started = entering.previewImageCount > 0 && entering.previewHasPendingWork }
            if (!started) SystemClock.sleep(10)
        }
        assertTrue(started)
        main { host.visibility = android.view.View.INVISIBLE }
        instrumentation.waitForIdleSync()
        main { assertFalse(entering.previewHasPendingWork) }
        SystemClock.sleep(150)
        main { assertFalse(entering.previewHasPendingWork); host.visibility = android.view.View.VISIBLE }
        instrumentation.waitForIdleSync()
        main {
            assertTrue(panel.beginBackPreview())
            panel.previewBack(0.4f)
            assertFalse(entering.previewHasPendingWork)
        }
        SystemClock.sleep(150)
        main { assertFalse(entering.previewHasPendingWork); panel.cancelBackPreview() }
        assertSame(entering, awaitSettled(panel, 3))
        main { assertTrue(panel.beginBackPreview()); panel.previewBack(0.6f); panel.finishBackPreview() }
        var released = false
        val releaseDeadline = SystemClock.uptimeMillis() + 3000
        while (!released && SystemClock.uptimeMillis() < releaseDeadline) {
            main { released = panel.childCount == 2 && entering.previewBitmapBytes == 0 && !entering.previewHasPendingWork }
            if (!released) SystemClock.sleep(30)
        }
        assertTrue("确认返回后卸载菜单和图像", released)
    }

    private fun menuOnMain(panel: ThemeSelectorPanel): ThemeDropdownMenu {
        lateinit var result: ThemeDropdownMenu
        main { result = menu(panel) }
        return result
    }

    @Test fun fractionalDensitiesKeepMenuInsideItsHeightBudget() {
        main {
            val choices = (0 until 24).map { ThemeOption.Placeholder("budget-$it", "Budget $it") }
            for (dpi in listOf(208, 420, 536)) {
                val context = activity.createConfigurationContext(android.content.res.Configuration().apply { densityDpi = dpi })
                for (budget in listOf(240, 360, 480)) {
                    val current = ThemeDropdownMenu(context, choices, null, budget, {}, {}) {}
                    assertTrue("density=$dpi budget=$budget actual=${current.contentHeight}", current.contentHeight <= budget)
                    current.updateHeightBudget(budget + 19)
                    assertTrue(current.contentHeight <= budget + 19)
                }
            }
        }
    }

}
