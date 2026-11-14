package com.otokit.noharayh.ui

import android.view.View
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsComponentsTest : UiComponentTestHost() {
    @Test fun selectionIsConfirmedExternallyAndUpdatesGeometryAndSemanticsTogether() {
        lateinit var button: SettingsButton
        var calls = 0
        main { button = SettingsPageViews.choice(activity, "Choice", false) { calls++ } }
        mount(button)
        main {
            button.performClick()
            assertEquals(1, calls)
            assertFalse(button.createAccessibilityNodeInfo().isChecked)
            button.renderSelection(true, false)
            assertTrue(button.createAccessibilityNodeInfo().isChecked)
            assertEquals(20f * activity.resources.displayMetrics.density, button.baseRadius, 1f)
            button.renderSelection(false, false)
            assertFalse(button.createAccessibilityNodeInfo().isChecked)
            assertEquals(4f * activity.resources.displayMetrics.density, button.baseRadius, 1f)
        }
    }

    @Test fun emptyCardHasOnlyHeaderAndRemovingContentRestoresItsHeight() {
        lateinit var card: SettingsCard
        main { card = SettingsCard(activity, R.drawable.ic_format_paint_24, R.string.settings_theme_selection) }
        mount(card, 800, -2)
        val emptyHeight = card.height
        assertEquals((52 * activity.resources.displayMetrics.density + 0.5f).toInt(), emptyHeight)
        main { card.setContent(View(activity).apply { minimumHeight = 100 }) }
        instrumentation.waitForIdleSync()
        assertTrue(card.height > emptyHeight)
        main { card.setContent(null) }
        instrumentation.waitForIdleSync()
        assertEquals(emptyHeight, card.height)
        assertEquals(1, card.childCount)
    }

    @Test fun groupReassignsCornersWhenMemberIsHiddenOrRemoved() {
        lateinit var group: SettingsGroup
        lateinit var first: GroupedSwitchRow
        lateinit var last: GroupedSwitchRow
        main {
            group = SettingsGroup(activity)
            first = GroupedSwitchRow(activity, "First", true, true, false, {})
            last = GroupedSwitchRow(activity, "Last", true, true, false, {})
            group.addView(first, LinearLayout.LayoutParams(-1, -2))
            group.addView(last, LinearLayout.LayoutParams(-1, -2))
        }
        mount(group, 800, -2)
        val density = activity.resources.displayMetrics.density
        main {
            assertEquals(4f * density, first.bottomCornerRadius!!, 1f)
            assertEquals(4f * density, last.topCornerRadius!!, 1f)
            last.visibility = View.GONE
        }
        instrumentation.waitForIdleSync()
        main {
            assertEquals(16f * density, first.bottomCornerRadius!!, 1f)
            last.visibility = View.VISIBLE
            group.removeView(first)
        }
        instrumentation.waitForIdleSync()
        main { assertEquals(16f * density, last.topCornerRadius!!, 1f) }
    }
}
