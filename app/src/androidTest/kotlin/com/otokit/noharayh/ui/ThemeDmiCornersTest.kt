package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.ThemeDropdownMenu
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import com.otokit.noharayh.ui.settings.components.ThemeSettingsContent
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class ThemeDmiCornersTest : UiComponentTestHost() {
    private val density get() = activity.resources.displayMetrics.density
    private fun menu(panel: ThemeSelectorPanel) = descendants(panel).filterIsInstance<ThemeDropdownMenu>().singleOrNull()
    private fun field(panel: ThemeSelectorPanel) = panel.getChildAt(0) as LabeledDropdownField
    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }
    private fun awaitState(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            main { ready = condition() }
            if (!ready) SystemClock.sleep(16)
        }
        assertTrue("圆角及菜单应收敛到当前目标", ready)
    }
    private fun interactive(panel: ThemeSelectorPanel) =
        menu(panel)?.let { (it.getChildAt(0) as ViewGroup).getChildAt(0).isEnabled } == true

    /** 对齐到底边取背景像素，检查真实 G2 底形，而不是只检查回调数值。 */
    private fun cornerPixels(card: SettingsCard, bottom: Boolean): IntArray {
        val edge = (48f * density).roundToInt().coerceAtMost(card.height)
        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (bottom) canvas.translate(0f, -(card.height - edge).toFloat())
        card.background.draw(canvas)
        val result = IntArray(edge * edge)
        bitmap.getPixels(result, 0, edge, 0, 0, edge, edge)
        bitmap.recycle()
        return result.map(Color::alpha).toIntArray()
    }

    private fun cornersDiffer(first: IntArray, second: IntArray): Boolean =
        first.indices.any { kotlin.math.abs(first[it] - second[it]) > 200 }

    private fun assertSameCorner(first: IntArray, second: IntArray) {
        assertEquals(first.size, second.size)
        // 高度变化后底边的浮点坐标会改变 Skia 边缘覆盖取整，只比较明确的内外区域。
        assertFalse("非抗锯齿区域的轮廓应保持一致", cornersDiffer(first, second))
    }

    @Test fun collapsedCornersExpandAndPredictiveBackCancelsWithoutLosingTheLastRowTarget() {
        lateinit var panel: ThemeSelectorPanel
        val options = (1..10).map { ThemeOption.Placeholder("corner-$it", "Choice $it") }
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Corners")) {} }
        mount(panel, 800, 1600)
        main {
            assertEquals(4f * density, field(panel).baseRadius, 1f)
            panel.open(options, (360f * density).roundToInt()) {}
        }
        awaitState { interactive(panel) }
        var expandedBottom = 0f
        main {
            assertEquals(22f * density, field(panel).baseRadius, 1f)
            expandedBottom = field(panel).panelBottomCornerRadius
            assertEquals(menu(panel)!!.bottomCornerRadius + (12f * density).roundToInt(), expandedBottom, 1f)
            assertTrue(panel.beginBackPreview())
            panel.previewBack(.85f)
            assertTrue(field(panel).baseRadius > 4f * density - 1f)
            assertTrue(field(panel).baseRadius < 22f * density)
            assertTrue(field(panel).panelBottomCornerRadius < expandedBottom)
            panel.cancelBackPreview()
        }
        awaitState { interactive(panel) }
        main {
            assertEquals(expandedBottom, field(panel).panelBottomCornerRadius, .01f)
            assertTrue(panel.beginBackPreview())
            panel.previewBack(.5f)
            panel.finishBackPreview()
        }
        awaitState { menu(panel) == null }
        main {
            assertEquals(4f * density, field(panel).baseRadius, 1f)
            assertEquals(0f, panel.bottomCornerExtension, 0f)
        }
    }

    @Test fun onlyTheLastVisibleDmiDrivesTheCardAndMenuScrollingKeepsItsOriginalBottomRule() {
        lateinit var content: ThemeSettingsContent
        lateinit var panels: List<ThemeSelectorPanel>
        lateinit var card: SettingsCard
        val state = GameSelectionState(currentGame = GameLine.Mai,
            maiThemeId = ThemeOptions.resolveForGame(GameLine.Mai, null).id,
            chuThemeId = ThemeOptions.resolveForGame(GameLine.Chu, null).id,
            themeMode = ThemeSelectionMode.Independent)
        main {
            content = ThemeSettingsContent(activity, 12, false, state, {}, { _, _ -> }, {}, {}, { _, _ -> })
            panels = descendants(content).filterIsInstance<ThemeSelectorPanel>().toList()
            card = descendants(content).filterIsInstance<SettingsCard>().single()
        }
        mount(content, 1000, 2100)
        lateinit var originalBottom: IntArray
        lateinit var originalTop: IntArray
        main {
            originalBottom = cornerPixels(card, true)
            originalTop = cornerPixels(card, false)
            field(panels[0]).performClick()
        }
        awaitState { interactive(panels[0]) }
        main {
            assertTrue(panels[0].bottomCornerExtension > 0f)
            assertSameCorner(originalBottom, cornerPixels(card, true))
            assertSameCorner(originalTop, cornerPixels(card, false))
            field(panels[1]).performClick()
        }
        awaitState { interactive(panels[1]) && menu(panels[0]) == null }
        lateinit var foldedBottom: IntArray
        var foldedExtension = 0f
        main {
            foldedBottom = cornerPixels(card, true)
            foldedExtension = panels[1].bottomCornerExtension
            assertTrue(cornersDiffer(originalBottom, foldedBottom))
            assertSameCorner(originalTop, cornerPixels(card, false))
            menu(panels[1])!!.beginScrollbarDrag()
            menu(panels[1])!!.dragScrollbarTo(1f)
            menu(panels[1])!!.endScrollbarDrag()
        }
        awaitState {
            val rows = menu(panels[1])!!.getChildAt(0) as ViewGroup
            rows.getChildAt(rows.childCount - 1).isEnabled
        }
        main {
            assertTrue(panels[1].bottomCornerExtension > foldedExtension)
            assertTrue(cornersDiffer(foldedBottom, cornerPixels(card, true)))
            assertEquals(menu(panels[1])!!.bottomCornerRadius + (12f * density).roundToInt(),
                field(panels[1]).panelBottomCornerRadius, 1f)
            assertTrue(content.beginThemePanelBack())
            content.previewThemePanelBack(.85f)
            assertTrue(panels[1].bottomCornerExtension < foldedExtension)
            content.cancelThemePanelBack()
        }
        awaitState { menu(panels[1])!!.previewHasPendingWork.not() }
        main { content.closeThemePanel() }
        awaitState { menu(panels[1]) == null }
        main {
            assertSameCorner(originalBottom, cornerPixels(card, true))
            content.renderSelection(state.copy(themeMode = ThemeSelectionMode.Merged))
        }
        awaitState { panels[1].visibility == View.INVISIBLE && field(panels[0]).isEnabled }
        main { field(panels[0]).performClick() }
        awaitState { interactive(panels[0]) }
        main {
            assertTrue(cornersDiffer(originalBottom, cornerPixels(card, true)))
            content.renderSelection(state.copy(themeMode = ThemeSelectionMode.None))
        }
        awaitState { panels.all { menu(it) == null && it.visibility == View.INVISIBLE } }
        main {
            assertEquals(0f, panels[0].bottomCornerExtension, 0f)
            assertEquals(0f, panels[1].bottomCornerExtension, 0f)
        }
    }
}
