package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.settings.ExpandableContent
import com.otokit.noharayh.ui.kit.settings.ExpandableSettingsRow
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.ThemeSettingsContent
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsExpansionTest : UiComponentTestHost() {
    private fun awaitState(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3000
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            main { ready = condition() }
            if (!ready) SystemClock.sleep(16)
        }
        assertTrue("动画应收敛到最新确认状态", ready)
    }

    @Test fun interruptedExpansionUsesLatestStateAndRemeasuresContent() {
        lateinit var slot: ExpandableContent
        lateinit var body: View
        main {
            body = View(activity).apply { minimumHeight = 180 }
            slot = ExpandableContent(activity).apply { setContent(body); setExpanded(false, false) }
        }
        mount(slot, 600, -2)
        assertEquals(0, slot.height)
        main { slot.setExpanded(true) }
        SystemClock.sleep(80)
        main { slot.setExpanded(false) }
        awaitState { slot.progress == 0f && slot.height == 0 }
        main { slot.setExpanded(true) }
        awaitState { slot.progress == 1f && slot.height == 180 }
        main { body.minimumHeight = 260 }
        awaitState { slot.height == 260 }
    }

    @Test fun replacementKeepsLatestContentAndDetachSettlesWithoutPendingAnimation() {
        lateinit var slot: ExpandableContent
        lateinit var finalBody: View
        main {
            slot = ExpandableContent(activity).apply { setContent(View(activity).apply { minimumHeight = 100 }) }
            finalBody = View(activity).apply { minimumHeight = 240 }
        }
        mount(slot, 600, -2)
        main { slot.replaceContent(View(activity).apply { minimumHeight = 160 }) }
        SystemClock.sleep(60)
        main { slot.replaceContent(finalBody) }
        awaitState { slot.progress == 1f && slot.height == 240 }
        main {
            assertSame(finalBody, slot.getChildAt(0))
            slot.setExpanded(false)
            host.removeView(slot)
            assertEquals(0f, slot.progress, 0f)
        }
    }

    @Test fun expandedBodyReceivesTouchWithoutTogglingHeader() {
        lateinit var row: ExpandableSettingsRow
        lateinit var body: View
        var headerCalls = 0
        var bodyCalls = 0
        main {
            body = View(activity).apply { minimumHeight = 180; setOnClickListener { bodyCalls++ } }
            row = ExpandableSettingsRow(activity, "Expandable", false, { headerCalls++ }).apply {
                setContent(body); setExpanded(true, false)
            }
        }
        mount(row, 800, -2)
        touch(body, MotionEvent.ACTION_DOWN, Triple(0, 100f, 80f))
        touch(body, MotionEvent.ACTION_UP, Triple(0, 100f, 80f))
        main { assertEquals(1, bodyCalls); assertEquals(0, headerCalls) }
        touch(row, MotionEvent.ACTION_DOWN, Triple(0, 100f, 30f))
        touch(row, MotionEvent.ACTION_UP, Triple(0, 100f, 30f))
        main {
            assertEquals(1, headerCalls)
            row.setContent(null)
            assertEquals(1f, row.bottomCornerPressFraction, 0f)
        }
    }

    @Test fun cardCollapsesContentAndSpacingBackToHeader() {
        lateinit var card: SettingsCard
        main { card = SettingsCard(activity, R.drawable.ic_format_paint_24, R.string.settings_theme_selection,
            View(activity).apply { minimumHeight = 180 }) }
        mount(card, 800, -2)
        val header = (52 * activity.resources.displayMetrics.density + 0.5f).toInt()
        main { card.setContentExpanded(false) }
        awaitState { card.height == header }
        main { card.setContentExpanded(true) }
        awaitState { card.height > header + 180 }
    }

    @Test fun replacingCardContentPreservesCollapsedStateAndLatestReplacement() {
        lateinit var card: SettingsCard
        lateinit var finalBody: View
        main {
            card = SettingsCard(activity, R.drawable.ic_format_paint_24, R.string.settings_theme_selection,
                View(activity).apply { minimumHeight = 100 })
            finalBody = View(activity).apply { minimumHeight = 240 }
            card.setContentExpanded(false, false)
        }
        mount(card, 800, -2)
        val header = card.height
        lateinit var firstReplacement: View
        main {
            firstReplacement = View(activity).apply { minimumHeight = 160 }
            card.replaceContent(firstReplacement)
        }
        instrumentation.waitForIdleSync()
        assertEquals(header, card.height)
        main { card.setContentExpanded(true, false) }
        main { card.replaceContent(View(activity).apply { minimumHeight = 200 }) }
        SystemClock.sleep(40)
        main { card.replaceContent(finalBody) }
        awaitState { finalBody.parent != null && finalBody.alpha == 1f && card.height > header + 240 }
        main {
            card.setContent(firstReplacement)
            assertNull(finalBody.parent) // 卸下的内容必须释放父关系，允许调用方再次复用。
        }
    }

    @Test fun themeCompositionPreservesNoThemeHeaderAndExpandableBodyAcrossModeChanges() {
        lateinit var content: ThemeSettingsContent
        val state = GameSelectionState(GameLine.Mai, ThemeOptions.PLACEHOLDER_ID, ThemeOptions.PLACEHOLDER_ID)
        main { content = ThemeSettingsContent(activity, 12, false, state, {}, { _, _ -> }, {}, {}, { _, _ -> }) }
        mount(content, 1100, 1300)
        fun descendants(view: View): List<View> = listOf(view) +
            if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()
        lateinit var card: SettingsCard
        lateinit var row: ExpandableSettingsRow
        main {
            card = descendants(content).filterIsInstance<SettingsCard>().single()
            row = descendants(content).filterIsInstance<ExpandableSettingsRow>().single()
            content.renderSelection(state.copy(themeMode = ThemeSelectionMode.None))
        }
        val header = (52 * activity.resources.displayMetrics.density + 0.5f).toInt()
        awaitState { card.height == header && row.isEnabled }
        main { content.renderSelection(state.copy(themeMode = ThemeSelectionMode.None, noneThemeUseWallpaper = false)) }
        awaitState { row.height > header + 250 }
        main { content.renderSelection(state.copy(themeMode = ThemeSelectionMode.Merged)) }
        SystemClock.sleep(80)
        main { content.renderSelection(state.copy(themeMode = ThemeSelectionMode.None)) }
        awaitState { card.height == header && row.height == header && row.isEnabled }
    }
}
