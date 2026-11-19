package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.ui.kit.common.InfoIconButton
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.settings.subPages.ScoreSyncSettingsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class GroupedSwitchInfoTest : UiComponentTestHost() {
    @Test fun infoHotAreaHasCenteredFeedbackAndNeverPressesOrTogglesTheRow() {
        var changes = 0
        var infoClicks = 0
        lateinit var row: GroupedSwitchRow
        main { row = GroupedSwitchRow(activity, "设置说明", true, true, false,
            { changes++ }, onInfoClick = { infoClicks++ }) }
        mount(row, activity.resources.displayMetrics.widthPixels - 160, -2)
        val info = findInfo(row)!!
        val density = activity.resources.displayMetrics.density
        assertEquals(48f * density, info.width.toFloat(), 1f)
        assertEquals(48f * density, info.height.toFloat(), 1f)
        val icon = info.getChildAt(0)
        val iconGap = if (info.layoutDirection == View.LAYOUT_DIRECTION_RTL) info.width - icon.right else icon.left
        assertEquals(4f * density, iconGap.toFloat(), 1f)
        val backdrop = Color.rgb(226, 232, 240)
        fun drawnIconWidth(pressed: Boolean): Int {
            val bitmap = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(backdrop)
                info.draw(canvas)
                val midX = icon.left + icon.width / 2
                val midY = bitmap.height / 2
                val outerX = if (info.layoutDirection == View.LAYOUT_DIRECTION_RTL) 4 else bitmap.width - 5
                assertEquals(backdrop, bitmap.getPixel(outerX, midY))
                if (pressed) {
                    val distance = (12 * density).toInt()
                    val left = Color.red(bitmap.getPixel(midX - distance, midY))
                    val right = Color.red(bitmap.getPixel(midX + distance, midY))
                    assertTrue(left < Color.red(backdrop))
                    assertEquals(left.toFloat(), right.toFloat(), 2f)
                }
                val glyph = (0 until bitmap.width).filter { Color.red(bitmap.getPixel(it, midY)) < 128 }
                return glyph.last() - glyph.first() + 1
            } finally { bitmap.recycle() }
        }
        var restWidth = 0
        main { restWidth = drawnIconWidth(false) }
        // 点在热区边缘；反馈必须仍从图标中心出现，父行不进入按压状态。
        touch(info, MotionEvent.ACTION_DOWN, Triple(0, 4f, 4f))
        SystemClock.sleep(220)
        main {
            assertFalse(row.isPressed)
            assertTrue(info.isPressed)
            assertTrue(drawnIconWidth(true) < restWidth)
        }
        touch(info, MotionEvent.ACTION_UP, Triple(0, 4f, 4f))
        SystemClock.sleep(220)
        assertEquals(1, infoClicks)
        assertEquals(0, changes)
        main { assertEquals(restWidth, drawnIconWidth(false)) }
        touch(row, MotionEvent.ACTION_DOWN, Triple(0, 10f, row.height / 2f))
        touch(row, MotionEvent.ACTION_UP, Triple(0, 10f, row.height / 2f))
        assertEquals(1, changes)
        touch(info, MotionEvent.ACTION_DOWN, Triple(0, 4f, 4f))
        touch(info, MotionEvent.ACTION_MOVE, Triple(0, -20f, 4f))
        touch(info, MotionEvent.ACTION_UP, Triple(0, -20f, 4f))
        SystemClock.sleep(220)
        assertEquals(1, infoClicks)
        assertEquals(1, changes)
        main { row.isEnabled = false; assertFalse(info.isEnabled) }
    }

    @Test fun infoOpensExistingDialogWithoutChangingPreferenceAndDismissesWithPage() {
        val preferences = capturePreferences(activity)
        val original = preferences.keepCallbackCaptureUntilLogin()
        try {
            preferences.setKeepCallbackCaptureUntilLogin(false)
            lateinit var page: View
            main { page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {}) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            val info = findInfo(page, activity.getString(R.string.settings_capture_keep_callback_info))!!
            screenshot("capture-info-row.png")
            touch(info, MotionEvent.ACTION_DOWN, Triple(0, info.width / 2f, info.height / 2f))
            SystemClock.sleep(200)
            screenshot("capture-info-pressed.png")
            touch(info, MotionEvent.ACTION_UP, Triple(0, info.width / 2f, info.height / 2f))
            SystemClock.sleep(300)
            val message = activity.getString(R.string.settings_capture_keep_callback_description)
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(message).isNotEmpty())
            assertFalse(preferences.keepCallbackCaptureUntilLogin())
            screenshot("capture-info-dialog.png")
            val switchAction = instrumentation.uiAutomation.rootInActiveWindow
                .findAccessibilityNodeInfosByText(activity.getString(R.string.settings_info_plain)).single { it.isClickable }
            switchAction.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
            SystemClock.sleep(800)
            val plain = activity.getString(R.string.settings_capture_keep_callback_plain)
            assertTrue(plain.contains("\"获取各个难度成绩\""))
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(plain).isNotEmpty())
            assertFalse(preferences.keepCallbackCaptureUntilLogin())
            screenshot("capture-info-plain.png")
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            main { info.performClick() }
            main { host.removeView(page) }
            instrumentation.waitForIdleSync()
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(message).isEmpty())
        } finally { preferences.setKeepCallbackCaptureUntilLogin(original) }
    }

    private fun findInfo(view: View, description: String? = null): InfoIconButton? {
        if (view is InfoIconButton && (description == null || view.contentDescription == description)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findInfo(view.getChildAt(index), description)?.let { return it }
        }
        return null
    }

    private fun screenshot(name: String) {
        val bitmap = instrumentation.uiAutomation.takeScreenshot()!!
        try {
            File(activity.getExternalFilesDir(null), name).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally { bitmap.recycle() }
    }
}
