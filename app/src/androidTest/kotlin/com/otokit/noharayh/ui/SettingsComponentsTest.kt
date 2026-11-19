package com.otokit.noharayh.ui

import android.view.View
import android.os.SystemClock
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.InfoIconButton
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
        main { card = SettingsCard(activity, R.drawable.ic_wallpaper_40, R.string.settings_theme_selection) }
        mount(card, 800, -2)
        val emptyHeight = card.height
        assertEquals(44f * activity.resources.displayMetrics.density, emptyHeight.toFloat(), 1f)
        main { card.setContent(View(activity).apply { minimumHeight = 100 }) }
        instrumentation.waitForIdleSync()
        assertTrue(card.height > emptyHeight)
        main { card.setContent(null) }
        instrumentation.waitForIdleSync()
        assertEquals(emptyHeight, card.height)
        assertEquals(1, card.childCount)
    }

    @Test fun infoKeepsFullHitAreaWithoutMovingTheFirstContentRow() {
        lateinit var info: InfoIconButton
        lateinit var plainCard: SettingsCard
        lateinit var infoCard: SettingsCard
        lateinit var plainAction: SettingsButton
        lateinit var infoAction: SettingsButton
        lateinit var group: SettingsGroup
        var infoClicks = 0
        var actionClicks = 0
        main {
            info = InfoIconButton(activity, "说明") { infoClicks++ }
            plainAction = SettingsPageViews.action(activity, "查看记录") {}
            infoAction = SettingsPageViews.action(activity, "查看记录") { actionClicks++ }
            plainCard = SettingsCard(activity, R.drawable.ic_wallpaper_40, R.string.settings_theme_selection, plainAction)
            infoCard = SettingsCard(activity, R.drawable.ic_wallpaper_40, R.string.settings_theme_selection,
                infoAction, headerInfo = info)
            group = SettingsGroup(activity).apply {
                addView(plainCard, LinearLayout.LayoutParams(-1, -2))
                addView(infoCard, LinearLayout.LayoutParams(-1, -2))
            }
        }
        mount(group, 800, -2)
        val density = activity.resources.displayMetrics.density
        assertEquals(44f * density, plainAction.top.toFloat(), 1f)
        assertEquals(plainAction.top, infoAction.top)
        assertEquals(plainCard.height, infoCard.height)
        assertEquals(48f * density, info.height.toFloat(), 1f)
        assertTrue(info.bottom > infoAction.top)
        val point = Triple(0, info.width / 2f, info.height - 2f)
        touch(info, MotionEvent.ACTION_DOWN, point)
        touch(info, MotionEvent.ACTION_UP, point)
        awaitState { infoClicks == 1 }
        assertEquals(0, actionClicks)
        val actionPoint = Triple(0, infoAction.width / 2f, infoAction.height / 2f)
        touch(infoAction, MotionEvent.ACTION_DOWN, actionPoint)
        touch(infoAction, MotionEvent.ACTION_UP, actionPoint)
        assertEquals(1, actionClicks)
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
        assertEquals(48f * density, first.height.toFloat(), 1f)
        awaitState { first.bottomCornerRadius == 4f * density }
        main {
            assertEquals(4f * density, first.bottomCornerRadius!!, 1f)
            assertEquals(4f * density, last.topCornerRadius!!, 1f)
            last.visibility = View.GONE
        }
        instrumentation.waitForIdleSync()
        awaitState { first.bottomCornerRadius == 16f * density }
        main {
            assertEquals(16f * density, first.bottomCornerRadius!!, 1f)
            last.visibility = View.VISIBLE
            group.removeView(first)
        }
        instrumentation.waitForIdleSync()
        awaitState { last.topCornerRadius == 16f * density }
        main { assertEquals(16f * density, last.topCornerRadius!!, 1f) }
    }

    @Test fun cardBackgroundUpdatesGroupedCornersWithoutChangingBounds() {
        lateinit var card: SettingsCard
        main { card = SettingsCard(activity, R.drawable.ic_wallpaper_40, R.string.settings_theme_selection) }
        mount(card, 800, -2)
        main {
            fun cornerAlpha(): Int {
                val bitmap = android.graphics.Bitmap.createBitmap(card.width, card.height,
                    android.graphics.Bitmap.Config.ARGB_8888)
                card.background.draw(android.graphics.Canvas(bitmap))
                val offset = (3f * activity.resources.displayMetrics.density).toInt()
                return android.graphics.Color.alpha(bitmap.getPixel(offset, offset)).also { bitmap.recycle() }
            }
            card.renderGroupEdges(1f, 1f)
            assertEquals(0, cornerAlpha())
            card.renderGroupEdges(0f, 0f)
            assertEquals(255, cornerAlpha())
            card.renderGroupEdges(1f, 1f)
            assertEquals(0, cornerAlpha())
        }
    }

    private fun awaitState(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 2500
        var ready = false
        while (!ready && SystemClock.uptimeMillis() < deadline) {
            main { ready = condition() }
            if (!ready) SystemClock.sleep(16)
        }
        assertTrue(ready)
    }
}
