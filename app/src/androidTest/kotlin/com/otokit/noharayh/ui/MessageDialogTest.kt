package com.otokit.noharayh.ui

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.dialog.AlternateExplanation
import com.otokit.noharayh.ui.kit.common.dialog.DialogMessageContent
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialogAction
import com.otokit.noharayh.ui.kit.settings.SettingsTextField
import com.otokit.noharayh.ui.kit.common.ContentScrollView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class MessageDialogTest : UiComponentTestHost() {
    @Test fun shortFormWindowClipsLongContentAboveItsVisibleActionsOnHardware() {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).roundToInt()
        lateinit var dialog: MessageDialog
        lateinit var scroll: ContentScrollView
        lateinit var save: View
        lateinit var cancel: View
        main {
            val longContent = View(activity).apply { minimumHeight = dp(900); setBackgroundColor(Color.MAGENTA) }
            dialog = MessageDialog(activity, R.drawable.ic_info_outline_24, "长表单说明", "保存", "取消",
                customContent = longContent, onConfirmed = {})
            dialog.showWithin(activity.resources.displayMetrics.widthPixels)
            dialog.window!!.setLayout(dialog.window!!.attributes.width, dp(360))
        }
        try {
            awaitFocus(dialog)
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(500, 3000)
            val points = mutableListOf<Pair<Int, Int>>()
            var actionTop = 0
            main {
                val decor = dialog.window!!.decorView
                scroll = descendants(decor).filterIsInstance<ContentScrollView>().single()
                save = descendants(decor).single { it.contentDescription == "保存" }
                cancel = descendants(decor).single { it.contentDescription == "取消" }
                assertTrue(scroll.canScrollVertically(1))
                assertTrue(scroll.height > 0 && save.isShown && cancel.isShown)
                val viewport = IntArray(2).also(scroll::getLocationOnScreen)
                val left = IntArray(2).also(save::getLocationOnScreen)
                val right = IntArray(2).also(cancel::getLocationOnScreen)
                actionTop = left[1]
                assertTrue(viewport[1] + scroll.height < actionTop)
                points += viewport[0] + scroll.width / 2 to viewport[1] + scroll.height - dp(8)
                points += viewport[0] + scroll.width / 2 to (viewport[1] + scroll.height + actionTop) / 2
                points += (left[0] + save.width + right[0]) / 2 to left[1] + save.height / 2
            }
            val bitmap = hardwareFrame(dialog.window!!.decorView)
            try {
                assertEquals("Long content must actually be visible", Color.MAGENTA, bitmap.getPixel(points[0].first, points[0].second))
                assertEquals("Content must not paint the gap above actions", Color.WHITE, bitmap.getPixel(points[1].first, points[1].second))
                assertEquals("Content must not paint between actions", Color.WHITE, bitmap.getPixel(points[2].first, points[2].second))
                File(activity.cacheDir, "message-dialog-short-form.png").outputStream().use {
                    assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                }
            } finally { bitmap.recycle() }
            main { scroll.scrollTo(0, scroll.getChildAt(0).height) }
            instrumentation.waitForIdleSync()
            main {
                assertTrue(scroll.scrollY > 0)
                assertEquals(actionTop, bottomOnScreen(save) - save.height)
                assertTrue(save.isShown && cancel.isShown && save.isEnabled && cancel.isEnabled)
                cancel.performClick()
                assertFalse(dialog.isShowing)
            }
        } finally { main { dialog.dismiss() } }
    }

    @Test fun formContentKeepsItsDraftWhenSubmissionIsRejected() = main {
        val field = SettingsTextField(activity, "测试输入", "保留草稿")
        var valid = false
        var confirmed = 0
        val dialog = MessageDialog(activity, R.drawable.ic_info_outline_24, "表单说明", "保存", "取消",
            customContent = field, onSubmit = { valid }, onConfirmed = { confirmed++ })
        try {
            dialog.showWithin(activity.resources.displayMetrics.widthPixels)
            descendants(dialog.window!!.decorView).single { it.contentDescription == "保存" }.performClick()
            assertTrue(dialog.isShowing)
            assertEquals("保留草稿", field.text)
            assertEquals(0, confirmed)
            valid = true
            descendants(dialog.window!!.decorView).single { it.contentDescription == "保存" }.performClick()
            assertFalse(dialog.isShowing)
            assertEquals(1, confirmed)
        } finally { dialog.dismiss() }
    }

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

    @Test fun exportChoiceIgnoresOutsideTouchAndKeepsExplicitActions() {
        var opened = 0
        var shared = 0
        main {
            val ordinary = MessageDialog(activity, R.drawable.ic_info_outline_24, "正文", "确定", "取消") {}
            ordinary.showWithin(activity.resources.displayMetrics.widthPixels)
            outsideTouch(ordinary)
            assertFalse(ordinary.isShowing)

            val export = MessageDialog(activity, R.drawable.ic_archive_record_24, "导出正文", "打开", "取消",
                additionalAction = MessageDialogAction("分享", 0xFF2196F3.toInt()) { shared++ },
                cancelOnTouchOutside = false) { opened++ }
            try {
                export.showWithin(activity.resources.displayMetrics.widthPixels)
                outsideTouch(export)
                assertTrue(export.isShowing)
                assertEquals(0, opened + shared)
                descendants(export.window!!.decorView).single { it.contentDescription == "取消" }.performClick()
                assertFalse(export.isShowing)
                export.showWithin(activity.resources.displayMetrics.widthPixels)
                descendants(export.window!!.decorView).single { it.contentDescription == "打开" }.performClick()
                assertFalse(export.isShowing)
                assertEquals(1, opened)
                export.showWithin(activity.resources.displayMetrics.widthPixels)
                descendants(export.window!!.decorView).single { it.contentDescription == "分享" }.performClick()
                assertFalse(export.isShowing)
                assertEquals(1, shared)
            } finally { export.dismiss() }
        }
    }

    @Test fun exportChoiceSurvivesWindowFocusLossAndBackStillDismisses() {
        lateinit var export: MessageDialog
        lateinit var overlay: Dialog
        main {
            export = MessageDialog(activity, R.drawable.ic_archive_record_24, "导出正文", "打开", "取消",
                cancelOnTouchOutside = false) {}
            overlay = Dialog(activity).apply { setContentView(TextView(activity).apply { text = "临时窗口" }) }
            export.showWithin(activity.resources.displayMetrics.widthPixels)
        }
        try {
            awaitFocus(export)
            main { overlay.show() }
            awaitFocus(overlay)
            main {
                assertFalse(export.window!!.decorView.hasWindowFocus())
                assertTrue(export.isShowing)
                overlay.dismiss()
            }
            awaitFocus(export)
            main { assertTrue(export.isShowing) }
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            main { assertFalse(export.isShowing) }
        } finally { main { overlay.dismiss(); export.dismiss() } }
    }

    private fun awaitFocus(dialog: Dialog) {
        val deadline = SystemClock.uptimeMillis() + 3000
        while (SystemClock.uptimeMillis() < deadline) {
            var focused = false
            main { focused = dialog.window!!.decorView.hasWindowFocus() }
            if (focused) return
            SystemClock.sleep(16)
        }
        fail("弹窗未获得窗口焦点")
    }

    private fun hardwareFrame(view: View): Bitmap {
        val committed = CountDownLatch(1)
        main {
            assertTrue("The dialog must use hardware rendering", view.isHardwareAccelerated)
            view.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
            view.invalidate()
        }
        assertTrue("The dialog hardware frame must be submitted", committed.await(2, TimeUnit.SECONDS))
        return requireNotNull(instrumentation.uiAutomation.takeScreenshot())
    }

    private fun outsideTouch(dialog: MessageDialog) {
        val now = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(now, now, MotionEvent.ACTION_OUTSIDE, -1f, -1f, 0)
        try { dialog.onTouchEvent(event) } finally { event.recycle() }
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
