package com.otokit.noharayh.ui

import android.app.Dialog
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.TextView
import androidx.core.view.allViews
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import com.otokit.noharayh.ui.kit.common.InfoIconButton
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.settings.subPages.ScoreSyncSettingsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScoreSyncSettingsTest : UiComponentTestHost() {
    @Test fun settingSurvivesPageRebuildAndLaunchIntentKeepsItsOriginalValue() {
        val preferences = capturePreferences(activity)
        val original = preferences.keepCallbackCaptureUntilLogin()
        try {
            preferences.setKeepCallbackCaptureUntilLogin(false)
            lateinit var page: View
            main { page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {}) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            main {
                val toggle = switchIn(page, R.string.settings_capture_keep_callback)!!
                assertFalse(toggle.isChecked)
                toggle.performClick()
                assertTrue(toggle.isChecked)
                assertTrue(capturePreferences(activity).keepCallbackCaptureUntilLogin())
                val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "settings-test",
                    preferences.keepCallbackCaptureUntilLogin())
                val launch = NetCaptureService.startIntent(activity, request)
                host.removeView(page)
                page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {})
                val restored = switchIn(page, R.string.settings_capture_keep_callback)!!
                assertTrue(restored.isChecked)
                restored.performClick()
                assertFalse(restored.isChecked)
                assertFalse(capturePreferences(activity).keepCallbackCaptureUntilLogin())
                assertTrue(launch.getBooleanExtra("keepCallbackCaptureUntilLogin", false))
            }
        } finally { preferences.setKeepCallbackCaptureUntilLogin(original) }
    }

    @Test fun exportRedactionDefaultsOnAndSurvivesRebuildBesideCallbackCapture() {
        val storage = activity.getSharedPreferences("export_preferences", 0)
        val key = "redact_exports"
        val original = if (storage.contains(key)) storage.getBoolean(key, true) else null
        try {
            storage.edit().remove(key).commit()
            assertTrue(exportPreferences(activity).redactExports())
            lateinit var page: View
            main { page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {}) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            main {
                val redaction = switchIn(page, R.string.settings_export_redact)!!
                val callback = switchIn(page, R.string.settings_capture_keep_callback)!!
                assertTrue(redaction.isChecked)
                assertTrue(redaction.parent is SettingsGroup)
                assertSame(callback.parent, redaction.parent)
                redaction.performClick()
                assertFalse(redaction.isChecked)
                assertFalse(exportPreferences(activity).redactExports())
                host.removeView(page)
                page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {})
            }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            main {
                val restored = switchIn(page, R.string.settings_export_redact)!!
                assertFalse(restored.isChecked)
                restored.performClick()
                assertTrue(restored.isChecked)
                assertTrue(exportPreferences(activity).redactExports())
            }
            assertRedactionPlainExplanation(page)
        } finally {
            storage.edit().apply {
                if (original == null) remove(key) else putBoolean(key, original)
            }.commit()
        }
    }

    @Test fun returningWindowFocusRefreshesTheMountedRedactionSwitchOnly() {
        val storage = activity.getSharedPreferences("export_preferences", 0)
        val key = "redact_exports"
        val original = if (storage.contains(key)) storage.getBoolean(key, true) else null
        val exports = exportPreferences(activity)
        val callbackOriginal = capturePreferences(activity).keepCallbackCaptureUntilLogin()
        var overlay: Dialog? = null
        try {
            exports.setRedactExports(true)
            lateinit var page: View
            lateinit var redaction: GroupedSwitchRow
            main { page = ScoreSyncSettingsPage.create(activity, 20, true, {}, {}) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            awaitWindowCondition { page.hasWindowFocus() }
            main {
                redaction = switchIn(page, R.string.settings_export_redact)!!
                assertTrue(redaction.isChecked)
                overlay = Dialog(activity).apply {
                    setContentView(TextView(activity).apply { text = "临时导出窗口" })
                    show()
                }
            }
            awaitWindowCondition { overlay!!.window!!.decorView.hasWindowFocus() }
            main {
                assertFalse(page.hasWindowFocus())
                exports.setRedactExports(false)
                assertTrue(redaction.isChecked)
                overlay!!.dismiss()
            }
            awaitWindowCondition { page.hasWindowFocus() }
            main {
                assertSame(redaction, switchIn(page, R.string.settings_export_redact))
                assertFalse(redaction.isChecked)
                assertFalse(exports.redactExports())
                assertEquals(callbackOriginal, switchIn(page, R.string.settings_capture_keep_callback)!!.isChecked)
                assertEquals(callbackOriginal, capturePreferences(activity).keepCallbackCaptureUntilLogin())
            }
        } finally {
            main { overlay?.dismiss() }
            storage.edit().apply {
                if (original == null) remove(key) else putBoolean(key, original)
            }.commit()
        }
    }

    @Test fun loggingAndHtmlAttachmentDefaultOffAndKeepTheirOwnSavedValues() {
        val capture = activity.getSharedPreferences("capture_preferences", 0)
        val exports = activity.getSharedPreferences("export_preferences", 0)
        val logKey = "record_score_sync_logs"
        val htmlKey = "include_html_with_logs"
        val previousLog = if (capture.contains(logKey)) capture.getBoolean(logKey, false) else null
        val previousHtml = if (exports.contains(htmlKey)) exports.getBoolean(htmlKey, false) else null
        var overlay: Dialog? = null
        try {
            capture.edit().remove(logKey).commit()
            exports.edit().remove(htmlKey).commit()
            lateinit var page: View
            main { page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {}) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            main {
                val html = switchIn(page, R.string.settings_export_logs_html)!!
                val redaction = switchIn(page, R.string.settings_export_redact)!!
                val log = switchIn(page, R.string.settings_sync_logs_save)!!
                assertFalse((page as android.widget.ScrollView).isVerticalScrollBarEnabled)
                assertFalse((page as android.widget.ScrollView).isHorizontalScrollBarEnabled)
                assertFalse(html.isChecked)
                assertFalse(log.isChecked)
                assertEquals(View.GONE, html.visibility)
                val group = html.parent as SettingsGroup
                assertSame(group, redaction.parent)
                assertSame(group, log.parent)
                val callback = switchIn(page, R.string.settings_capture_keep_callback)!!
                assertEquals(group.indexOfChild(callback) + 1, group.indexOfChild(redaction))
                assertEquals(group.indexOfChild(redaction) + 1, group.indexOfChild(log))
                assertEquals(group.indexOfChild(log) + 1, group.indexOfChild(html))
                log.performClick()
            }
            awaitHtmlVisibility(page, true)
            main {
                val html = switchIn(page, R.string.settings_export_logs_html)!!
                val log = switchIn(page, R.string.settings_sync_logs_save)!!
                html.performClick()
                assertTrue(exportPreferences(activity).includeHtmlWithLogs())
                assertTrue(capturePreferences(activity).recordScoreSyncLogs())
                log.performClick()
                assertTrue(exportPreferences(activity).includeHtmlWithLogs())
            }
            awaitHtmlVisibility(page, false)
            main {
                val html = switchIn(page, R.string.settings_export_logs_html)!!
                val log = switchIn(page, R.string.settings_sync_logs_save)!!
                log.performClick()
                assertTrue(html.isChecked)
            }
            awaitHtmlVisibility(page, true)
            main {
                host.removeView(page)
                page = ScoreSyncSettingsPage.create(activity, 20, false, {}, {})
            }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            awaitHtmlVisibility(page, true)
            main {
                assertTrue(switchIn(page, R.string.settings_export_logs_html)!!.isChecked)
                assertTrue(switchIn(page, R.string.settings_sync_logs_save)!!.isChecked)
                assertEquals(View.VISIBLE, switchIn(page, R.string.settings_export_logs_html)!!.visibility)
            }
            awaitWindowCondition { page.hasWindowFocus() }
            main {
                overlay = Dialog(activity).apply {
                    setContentView(TextView(activity).apply { text = "临时窗口" })
                    show()
                }
            }
            awaitWindowCondition { overlay!!.window!!.decorView.hasWindowFocus() }
            main { capturePreferences(activity).setRecordScoreSyncLogs(false); overlay!!.dismiss() }
            awaitWindowCondition { page.hasWindowFocus() }
            awaitHtmlVisibility(page, false)
            main {
                val html = switchIn(page, R.string.settings_export_logs_html)!!
                val log = switchIn(page, R.string.settings_sync_logs_save)!!
                assertFalse(log.isChecked)
                assertEquals(View.GONE, html.visibility)
                assertTrue(exportPreferences(activity).includeHtmlWithLogs())
                log.performClick()
                assertTrue(html.isChecked)
            }
            awaitHtmlVisibility(page, true)
            assertPlainExplanation(page, R.string.settings_export_logs_html_info, "我想要说的前人们都说过了——")
            assertTrue(exportPreferences(activity).includeHtmlWithLogs())
            assertTrue(capturePreferences(activity).recordScoreSyncLogs())
        } finally {
            main { overlay?.dismiss() }
            capture.edit().apply { if (previousLog == null) remove(logKey) else putBoolean(logKey, previousLog) }.commit()
            exports.edit().apply { if (previousHtml == null) remove(htmlKey) else putBoolean(htmlKey, previousHtml) }.commit()
        }
    }

    private fun awaitHtmlVisibility(page: View, visible: Boolean) {
        awaitWindowCondition {
            switchIn(page, R.string.settings_export_logs_html)!!.visibility == if (visible) View.VISIBLE else View.GONE
        }
    }

    private fun awaitWindowCondition(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("The expected window focus did not arrive")
    }

    private fun assertRedactionPlainExplanation(page: View) {
        assertPlainExplanation(page, R.string.settings_export_redact_info,
            "HTML记录和同步日志内包含您在微信内登录舞萌|中二时所需的敏感信息，脱敏将会在导出时将这些信息进行混淆。")
        assertTrue(exportPreferences(activity).redactExports())
    }

    private fun assertPlainExplanation(page: View, infoLabel: Int, expected: String) {
        main {
            page.allViews.filterIsInstance<InfoIconButton>().single {
                it.contentDescription == activity.getString(infoLabel)
            }.performClick()
        }
        instrumentation.waitForIdleSync()
        val action = awaitActiveNode(activity.getString(R.string.settings_info_plain)) {
            it.isVisibleToUser && it.isClickable && it.isEnabled
        }
        assertTrue(action.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        awaitActiveNode(activity.getString(R.string.settings_info_technical)) {
            it.isVisibleToUser && it.isClickable && it.isEnabled
        }
        val text = awaitActiveNode(expected) {
            it.isVisibleToUser && it.className == "android.widget.TextView"
        }
        assertEquals(expected, text.text.toString())
    }

    private fun awaitActiveNode(text: String, matches: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo {
        var windowWasReadable = false
        val deadline = SystemClock.uptimeMillis() + 3000
        do {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                windowWasReadable = true
                root.findAccessibilityNodeInfosByText(text).singleOrNull(matches)?.let { return it }
            }
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        error(if (windowWasReadable) "The active window was readable, but the expected node was not ready: $text"
            else "The active accessibility window never became ready: $text")
    }

    private fun switchIn(view: View, label: Int): GroupedSwitchRow? {
        if (view is GroupedSwitchRow && view.contentDescription == activity.getString(label)) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            switchIn(view.getChildAt(index), label)?.let { return it }
        }
        return null
    }
}
