package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.ThemeDropdownMenu
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import com.otokit.noharayh.ui.theme.ThemeOption
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

    private fun awaitInteractive(panel: ThemeSelectorPanel) {
        // 等待真实展开到可交互态，不强行结束内部 runner。
        val timeout = SystemClock.uptimeMillis() + 2500
        var enabled = false
        while (!enabled && SystemClock.uptimeMillis() < timeout) {
            main { enabled = (menu(panel)!!.getChildAt(0) as ViewGroup).getChildAt(0).isEnabled }
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
            assertEquals("New 1", (row.getChildAt(0) as TextView).text)
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
}
