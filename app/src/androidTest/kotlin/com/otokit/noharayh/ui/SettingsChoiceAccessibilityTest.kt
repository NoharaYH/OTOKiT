package com.otokit.noharayh.ui

import android.view.View
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.kit.settings.SettingsSegmentedChoice
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsChoiceAccessibilityTest : UiComponentTestHost() {
    @Test fun segmentedSelectionFollowsConfirmedValueBeforeAnimationFinishes() {
        lateinit var segment: SettingsSegmentedChoice
        var intent = -1
        main {
            segment = SettingsSegmentedChoice(activity)
            segment.setItems(listOf("First audit option", "Second audit option", "Third audit option"), 0) { intent = it }
        }
        mount(segment, 900, 100)
        main {
            assertChoice(segment.getChildAt(0), true)
            assertChoice(segment.getChildAt(1), false)
            val group = segment.createAccessibilityNodeInfo().collectionInfo!!
            assertEquals(3, group.columnCount)
            assertEquals(AccessibilityNodeInfo.CollectionInfo.SELECTION_MODE_SINGLE, group.selectionMode)
            segment.getChildAt(1).performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
            assertEquals(1, intent)
            assertChoice(segment.getChildAt(0), true) // 意图不等于确认。
            segment.setSelection(1)
            assertChoice(segment.getChildAt(0), false)
            assertChoice(segment.getChildAt(1), true)
            assertEquals(1, segment.getChildAt(1).createAccessibilityNodeInfo().collectionItemInfo!!.columnIndex)
            segment.setSelection(2, animate = false)
            assertChoice(segment.getChildAt(1), false)
            assertChoice(segment.getChildAt(2), true)
        }
        instrumentation.waitForIdleSync()
        val nodes = accessibleRoot()
            .findAccessibilityNodeInfosByText("Third audit option")
        assertTrue("系统无障碍树应报告已确认选项", nodes.any { it.isChecked && it.isSelected })
    }

    @Test fun choiceRowExposesRadioStateAndKeepsClickIntent() {
        lateinit var choice: View
        var clicks = 0
        main { choice = SettingsPageViews.choice(activity, "Audit scale choice", true) { clicks++ } }
        mount(choice)
        main {
            assertChoice(choice, true)
            choice.performAccessibilityAction(AccessibilityNodeInfo.ACTION_CLICK, null)
            assertEquals(1, clicks)
            choice.isEnabled = false
            assertFalse(choice.createAccessibilityNodeInfo().isEnabled)
        }
        val nodes = accessibleRoot()
            .findAccessibilityNodeInfosByText("Audit scale choice")
        // 文本搜索仍可返回标记为不重要的 TextView；朗读入口应只剩整行。
        val important = nodes.filter { it.isImportantForAccessibility }
        assertEquals("内部文字不应重复成为朗读入口", 1, important.size)
        assertTrue(important.single().isChecked)
    }

    @Test fun rebindingSegmentsReplacesOldSelectionSemantics() {
        lateinit var segment: SettingsSegmentedChoice
        main { segment = SettingsSegmentedChoice(activity).apply { setItems(listOf("A", "B"), 0) {} } }
        mount(segment)
        main {
            segment.setItems(listOf("C", "D", "E"), 2) {}
            assertChoice(segment.getChildAt(0), false)
            assertChoice(segment.getChildAt(2), true)
        }
    }

    private fun assertChoice(view: View, checked: Boolean) {
        val node = view.createAccessibilityNodeInfo()
        assertEquals("android.widget.RadioButton", node.className)
        assertTrue(node.isCheckable)
        assertEquals(checked, node.isChecked)
        assertEquals(checked, node.isSelected)
    }

    private fun accessibleRoot(): AccessibilityNodeInfo {
        val automation = instrumentation.uiAutomation
        // 测试服务默认可包含不重要节点；按普通读屏树验证，避免把诊断节点算作重复朗读。
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        }
        instrumentation.waitForIdleSync()
        return requireNotNull(automation.rootInActiveWindow)
    }
}
