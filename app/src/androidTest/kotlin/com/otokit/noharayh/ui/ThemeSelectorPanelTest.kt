package com.otokit.noharayh.ui

import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.ThemeMenuPreviewPalette
import com.otokit.noharayh.ui.kit.settings.ThemeDropdownMenu
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.constants.settings.ThemeMenuPreviewColors
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeRegistry
import com.otokit.noharayh.ui.settings.components.ThemeSettingsContent
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemeSelectorPanelTest : UiComponentTestHost() {
    private fun menu(panel: ThemeSelectorPanel): ThemeDropdownMenu? =
        (0 until panel.childCount).map(panel::getChildAt).filterIsInstance<ThemeDropdownMenu>().singleOrNull()

    @Test fun reopeningSameOptionsUsesLatestCallbackAndKeepsMenu() {
        lateinit var panel: ThemeSelectorPanel
        var oldCalls = 0
        var newCalls = 0
        val options = listOf(ThemeOption.Placeholder("one", "First"))
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Audit menu")) {} }
        mount(panel, 600, 600)
        main { panel.open(options, 400) { oldCalls++ } }
        awaitInteractive(panel)
        main {
            val original = menu(panel)
            panel.close()
            panel.open(options, 400) { newCalls++ }
            assertSame(original, menu(panel))
        }
        awaitInteractive(panel)
        main {
            (menu(panel)!!.getChildAt(0) as ViewGroup).getChildAt(0).performClick()
            assertEquals(0, oldCalls)
            assertEquals(1, newCalls)
        }
    }

    private fun awaitInteractive(panel: ThemeSelectorPanel, rowIndex: Int = 0) {
        // 等待真实展开到可交互态，不强行结束内部 runner。
        val timeout = SystemClock.uptimeMillis() + 2500
        var enabled = false
        while (!enabled && SystemClock.uptimeMillis() < timeout) {
            main { enabled = (menu(panel)!!.getChildAt(0) as ViewGroup).getChildAt(rowIndex).isEnabled }
            if (!enabled) SystemClock.sleep(16)
        }
        assertTrue(enabled)
    }

    @Test fun changedLabelsAndHeightBudgetAreNotDiscardedForSameIds() {
        lateinit var panel: ThemeSelectorPanel
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Audit menu")) {} }
        mount(panel, 600, 900)
        main {
            val options = (1..8).map { ThemeOption.Placeholder("$it", "Old $it") }
            panel.open(options, 160) {}
            val compact = menu(panel)!!
            panel.open(options.map { it.copy(title = "New ${it.id}") }, 160) {}
            val renamed = menu(panel)!!
            assertNotSame(compact, renamed)
            val row = (renamed.getChildAt(0) as ViewGroup).getChildAt(0) as ViewGroup
            assertEquals("New 1", label(row).text)
            panel.open(options.map { it.copy(title = "New ${it.id}") }, 800) {}
            assertTrue(menu(panel)!!.contentHeight > renamed.contentHeight)
        }
    }

    @Test fun emptyOptionsReleaseExistingMenu() {
        lateinit var panel: ThemeSelectorPanel
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Audit menu")) {} }
        mount(panel, 600, 600)
        main {
            panel.open(listOf(ThemeOption.Placeholder("one", "First")), 400) {}
            panel.open(emptyList(), 400) { error("空菜单不能派发选择") }
            assertFalse(panel.isOpen)
            assertNull(menu(panel))
            assertEquals(0f, panel.extraHeight, 0f)
        }
    }

    @Test fun confirmedChoiceUpdatesInPlaceAndFoldsWithoutLeakingItsContent() {
        lateinit var panel: ThemeSelectorPanel
        val options = (1..8).map { ThemeOption.Placeholder("$it", "Choice $it") }
        main { panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Choices")) {} }
        mount(panel, 600, 1000)
        main { panel.open(options, 800, selectedId = options.first().id) {} }
        awaitInteractive(panel)
        main {
            val original = menu(panel)!!
            val rows = original.getChildAt(0) as ViewGroup
            val first = rows.getChildAt(0) as SettingsButton
            val last = rows.getChildAt(options.lastIndex) as SettingsButton
            assertChoice(first, true)
            panel.renderSelection(options.last().id)
            assertSame(original, menu(panel))
            assertChoice(first, false)
            assertChoice(last, true)
            assertEquals(0f, last.getChildAt(0).alpha, 0f)
            assertFalse(last.isEnabled)
            panel.open(options, 800, selectedId = options.last().id) {}
            assertSame(original, menu(panel))
            original.beginScrollbarDrag()
            original.dragScrollbarTo(1f)
            original.endScrollbarDrag()
        }
        awaitInteractive(panel, options.lastIndex)
        main {
            val row = (menu(panel)!!.getChildAt(0) as ViewGroup).getChildAt(options.lastIndex) as SettingsButton
            assertChoice(row, true)
            assertEquals(1f, row.getChildAt(0).alpha, 0f)
            assertEquals(row.height, row.getChildAt(0).height)
            assertEquals(row.height / 2f, row.topCornerRadius!!, 1f)
            assertEquals(row.height / 2f, row.bottomCornerRadius!!, 1f)
        }
    }

    @Test fun settingsKeepConfirmedThemeInMergedAndBothIndependentMenus() {
        for (mode in listOf(ThemeSelectionMode.Merged, ThemeSelectionMode.Independent)) {
            lateinit var content: ThemeSettingsContent
            lateinit var panels: List<ThemeSelectorPanel>
            val state = GameSelectionState(currentGame = GameLine.Mai,
                maiThemeId = ThemeOptions.resolveForGame(GameLine.Mai, null).id,
                chuThemeId = ThemeOptions.resolveForGame(GameLine.Chu, null).id, themeMode = mode)
            main {
                host.removeAllViews()
                content = ThemeSettingsContent(activity, 12, false, state, {}, { _, _ -> }, {}, {}, { _, _ -> })
                panels = descendants(content).filterIsInstance<ThemeSelectorPanel>().toList()
            }
            mount(content, 600, 1000)
            val expected = if (mode == ThemeSelectionMode.Merged) listOf(ThemeOptions.merged() to state.mergedThemeId)
                else listOf(ThemeOptions.forGame(GameLine.Mai) to state.maiThemeId,
                    ThemeOptions.forGame(GameLine.Chu) to state.chuThemeId)
            expected.forEachIndexed { index, (options, selectedId) ->
                val panel = panels[index]
                main { panel.getChildAt(0).performClick() }
                awaitInteractive(panel)
                val selectedIndex = options.indexOfFirst { it.id == selectedId }
                main {
                    val current = menu(panel)!!
                    val rows = current.getChildAt(0) as ViewGroup
                    if (!rows.getChildAt(selectedIndex).isEnabled) {
                        val slots = (0 until rows.childCount).count { rows.getChildAt(it).isEnabled }
                        current.beginScrollbarDrag()
                        current.dragScrollbarTo((selectedIndex - slots + 1).toFloat() / (rows.childCount - slots))
                        current.endScrollbarDrag()
                    }
                }
                awaitInteractive(panel, selectedIndex)
                val previewDeadline = SystemClock.uptimeMillis() + 5000
                var pending = true
                while (pending && SystemClock.uptimeMillis() < previewDeadline) {
                    main { pending = menu(panel)!!.previewHasPendingWork }
                    if (pending) SystemClock.sleep(20)
                }
                assertFalse(pending)
                main {
                    val rows = menu(panel)!!.getChildAt(0) as ViewGroup
                    assertEquals(options.size, rows.childCount)
                    val selectedRows = (0 until rows.childCount).map(rows::getChildAt).filter { it.isSelected }
                    assertEquals(1, selectedRows.size)
                    val row = selectedRows.single() as SettingsButton
                    assertEquals(ThemeOptions.find(selectedId)!!.title, row.contentDescription)
                    val hasImage = options[selectedIndex].id == ThemeOptions.PLACEHOLDER_ID ||
                        (options[selectedIndex] as? ThemeOption.Html)?.theme?.definition?.preview != null
                    assertChoice(row, true, if (hasImage) Color.WHITE else SettingsComponentColors.TITLE)
                    assertEquals(row.height / 2f, row.topCornerRadius!!, 1f)
                    assertEquals(row.height / 2f, row.bottomCornerRadius!!, 1f)
                    row.performClick()
                    assertFalse(panel.isOpen)
                }
            }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }

    private fun label(row: ViewGroup): TextView = (row.getChildAt(0) as ViewGroup).getChildAt(0) as TextView

    private fun assertChoice(row: SettingsButton, selected: Boolean, textColor: Int = if (selected) SettingsComponentColors.TITLE else SettingsComponentColors.TEXT) {
        val node = row.createAccessibilityNodeInfo()
        assertEquals("android.widget.RadioButton", node.className)
        assertTrue(node.isCheckable)
        assertEquals(selected, node.isChecked)
        assertEquals(selected, node.isSelected)
        assertEquals(textColor, label(row).currentTextColor)
        assertEquals("预览不显示右侧单选圈，但保留单选语义", 1, (row.getChildAt(0) as ViewGroup).childCount)
    }

    @Test fun foldedThemesUseTheirExpandedUnderpaintAndMaterkitUpdatesWithoutRecreatingTheMenu() {
        lateinit var panel: ThemeSelectorPanel
        val html = ThemeRegistry.themesFor(GameLine.Mai).take(8).map(ThemeOption::Html)
        val options = html + ThemeOptions.placeholder
        main {
            panel = ThemeSelectorPanel(activity, LabeledDropdownField(activity, "Preview")) {}
            panel.renderMaterkitColors(ThemeColors(Color.RED, Color.BLUE))
        }
        mount(panel, 600, 1000)
        main { panel.open(options, 800, selectedId = ThemeOptions.PLACEHOLDER_ID) {} }
        awaitInteractive(panel)
        main {
            val original = menu(panel)!!
            val rows = original.getChildAt(0) as ViewGroup
            val themed = rows.getChildAt(7) as SettingsButton
            val native = rows.getChildAt(8) as SettingsButton
            fun primaryPixel(row: View): Int = pixel(row, row.width / 10, row.height / 2)
            assertEquals(ThemeMenuPreviewPalette.fromPrimary(html[7].theme.definition.colors.primaryColor).underpaint, primaryPixel(themed))
            assertEquals(ThemeMenuPreviewPalette.fromPrimary(Color.RED).underpaint, primaryPixel(native))
            panel.renderMaterkitColors(ThemeColors(Color.GREEN, Color.BLUE))
            assertSame(original, menu(panel))
            assertEquals(ThemeMenuPreviewPalette.fromPrimary(Color.GREEN).underpaint, primaryPixel(native))
            assertTrue(native.isSelected)
            original.beginScrollbarDrag()
            original.dragScrollbarTo(1f)
            original.endScrollbarDrag()
        }
        awaitInteractive(panel, options.lastIndex)
        main {
            val native = (menu(panel)!!.getChildAt(0) as ViewGroup).getChildAt(options.lastIndex)
            assertEquals(ThemeMenuPreviewColors.SELECTED, pixel(native, native.width * 3 / 4, native.height / 2))
            val stroke = pixel(native, native.width / 2, 2)
            assertEquals(Color.red(stroke), Color.green(stroke))
            assertEquals(Color.red(stroke), Color.blue(stroke))
            assertTrue(Color.red(stroke) < Color.red(ThemeMenuPreviewColors.SELECTED))
        }
    }

    private fun pixel(view: View, x: Int, y: Int): Int {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        return try { view.draw(Canvas(bitmap)); bitmap.getPixel(x, y) } finally { bitmap.recycle() }
    }
}
