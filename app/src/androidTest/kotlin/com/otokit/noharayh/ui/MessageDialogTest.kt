package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.dialog.AlternateExplanation
import com.otokit.noharayh.ui.kit.common.dialog.DialogMessageContent
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MessageDialogTest : UiComponentTestHost() {
    @Test fun shorterExplanationKeepsBaselineAndLongerOneResizesBothWaysWithButtons() {
        for (longer in listOf(false, true)) {
            lateinit var dialog: MessageDialog
            val primary = "技术说明正文。\n".repeat(4)
            val alternate = if (longer) "通俗说明正文。\n".repeat(9) else "简短的通俗说明。"
            main {
                dialog = MessageDialog(activity, R.drawable.ic_info_outline_24, primary, "已阅", null,
                    alternateExplanation = AlternateExplanation(alternate, "说人话", "高级点"), onConfirmed = {})
                dialog.showWithin(activity.resources.displayMetrics.widthPixels)
            }
            instrumentation.waitForIdleSync()
            try {
                lateinit var content: DialogMessageContent
                lateinit var right: View
                lateinit var left: View
                var baseline = 0
                var baselineBottom = 0
                main {
                    val views = descendants(dialog.window!!.decorView).toList()
                    content = views.filterIsInstance<DialogMessageContent>().single()
                    right = views.single { it.contentDescription == "说人话" }
                    left = views.single { it.contentDescription == "已阅" }
                    baseline = content.height
                    baselineBottom = bottomOnScreen(right)
                    right.performClick()
                }
                val forward = sample(content, right, left)
                val expanded = forward.last().height
                assertEquals("高级点", forward.last().label)
                assertTrue(forward.all { it.height >= baseline })
                assertTrue(forward.any { it.label == "说人话" && it.alpha < 0.8f })
                assertTrue(forward.any { it.label == "高级点" && it.alpha < 0.8f })
                assertTrue(forward.all { kotlin.math.abs(it.alpha - it.messageAlpha) < 0.01f })
                if (longer) {
                    assertTrue(expanded > baseline)
                    assertTrue(forward.any { it.height > baseline && it.height < expanded })
                    assertTrue(forward.last().bottom > baselineBottom)
                } else assertEquals(baseline, expanded)
                main { right.performClick() }
                val backward = sample(content, right, left)
                assertEquals(baseline, backward.last().height)
                assertEquals("说人话", backward.last().label)
                if (longer) assertTrue(backward.any { it.height > baseline && it.height < expanded })
                // 同一个 Dialog 中途关闭再显示，也必须恢复技术说明。
                main { right.performClick() }
                SystemClock.sleep(80)
                main { dialog.dismiss(); dialog.showWithin(activity.resources.displayMetrics.widthPixels) }
                instrumentation.waitForIdleSync()
                main {
                    assertFalse(content.showingAlternate)
                    assertEquals("说人话", right.contentDescription)
                    assertTrue(right.isEnabled)
                    assertEquals(baseline, content.height)
                }
            } finally { main { dialog.dismiss() } }
        }
    }

    @Test fun ordinaryConfirmationStillRequiresItsLeftAction() {
        var confirmations = 0
        lateinit var dialog: MessageDialog
        main {
            dialog = MessageDialog(activity, R.drawable.ic_info_outline_24, "确认正文", "确定", "取消") { confirmations++ }
            dialog.showWithin(activity.resources.displayMetrics.widthPixels)
            descendants(dialog.window!!.decorView).single { it.contentDescription == "取消" }.performClick()
            assertEquals(0, confirmations)
            assertFalse(dialog.isShowing)
            dialog.showWithin(activity.resources.displayMetrics.widthPixels)
            descendants(dialog.window!!.decorView).single { it.contentDescription == "确定" }.performClick()
            assertEquals(1, confirmations)
            assertFalse(dialog.isShowing)
        }
    }

    private data class Frame(val height: Int, val bottom: Int, val label: String, val alpha: Float, val messageAlpha: Float)

    private fun sample(content: DialogMessageContent, right: View, left: View): List<Frame> {
        val result = mutableListOf<Frame>()
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            var complete = false
            main {
                val label = descendants(right).filterIsInstance<TextView>().single()
                val message = descendants(content).filterIsInstance<TextView>().single { it.visibility == View.VISIBLE }
                assertEquals(bottomOnScreen(left), bottomOnScreen(right))
                result += Frame(content.height, bottomOnScreen(right), label.text.toString(), label.alpha, message.alpha)
                complete = right.isEnabled
            }
            if (complete) { instrumentation.waitForIdleSync(); return result }
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("说明切换未结束")
        return result
    }

    private fun bottomOnScreen(view: View): Int {
        val point = IntArray(2)
        view.getLocationOnScreen(point)
        return point[1] + view.height
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (index in 0 until view.childCount) yieldAll(descendants(view.getChildAt(index)))
    }
}
