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
        // 点在热区边缘；反馈必须仍从图标中心出现，父行不进入按压状态。
        touch(info, MotionEvent.ACTION_DOWN, Triple(0, 4f, 4f))
        SystemClock.sleep(220)
        main {
            assertFalse(row.isPressed)
            assertTrue(info.isPressed)
            val bitmap = Bitmap.createBitmap(info.width, info.height, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                info.draw(canvas)
                val radius = (11 * density).toInt()
                val left = bitmap.getPixel(info.width / 2 - radius, info.height / 2)
                val right = bitmap.getPixel(info.width / 2 + radius, info.height / 2)
                assertTrue(Color.red(left) < 250)
                assertEquals(Color.red(left).toFloat(), Color.red(right).toFloat(), 2f)
                assertEquals(Color.WHITE, bitmap.getPixel(4, 4))
            } finally { bitmap.recycle() }
        }
        touch(info, MotionEvent.ACTION_UP, Triple(0, 4f, 4f))
        SystemClock.sleep(220)
        assertEquals(1, infoClicks)
        assertEquals(0, changes)
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
            main { page = ScoreSyncSettingsPage.create(activity, 20, false) {} }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            val info = findInfo(page)!!
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
            instrumentation.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            instrumentation.waitForIdleSync()
            main { info.performClick() }
            main { host.removeView(page) }
            instrumentation.waitForIdleSync()
            assertTrue(instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByText(message).isEmpty())
        } finally { preferences.setKeepCallbackCaptureUntilLogin(original) }
    }

    private fun findInfo(view: View): InfoIconButton? {
        if (view is InfoIconButton) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            findInfo(view.getChildAt(index))?.let { return it }
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
