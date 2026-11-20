package com.otokit.noharayh.ui

import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.preferences.ExportPreferences
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.settings.components.ExportPrompts
import org.junit.Assert.*
import org.junit.Test

class ExportPromptsTest : UiComponentTestHost() {
    @Test fun logExportAsksRedactionThenHtmlBeforeContinuingAndRemembersBoth() {
        val stored = mutableMapOf<String, Boolean>()
        val preferences = ExportPreferences(stored::get) { key, value -> stored[key] = value }
        var shown: MessageDialog? = null
        var continued = 0
        val prompts = ExportPrompts(activity, preferences, { next ->
            shown = next
            next.showWithin(activity.resources.displayMetrics.widthPixels)
        }, { true })
        try {
            main { prompts.start(true) { continued++ } }
            val first = shown!!
            main {
                assertBluePath(first, "数据与同步设置-导出内容脱敏")
                assertEquals(0, continued)
                click(first, R.string.settings_export_warning_disable)
                assertFalse(first.isShowing)
                assertFalse(preferences.redactExports())
                assertTrue(preferences.exportWarningAcknowledged())
                val second = shown!!
                assertNotSame(first, second)
                val expected = "导出日志时可以选择是否携带与日志对应的HTML，以更方便的核对错误源。\n\n可在数据与同步设置-导出同步日志时携带HTML进行开关。"
                assertTrue(second.window!!.decorView.allViews.filterIsInstance<TextView>().any { it.text.toString() == expected })
                assertBluePath(second, "数据与同步设置-导出同步日志时携带HTML")
                assertEquals(0, continued)
                click(second, R.string.settings_log_html_include)
                assertTrue(preferences.includeHtmlWithLogs())
                assertTrue(preferences.htmlExportWarningAcknowledged())
                assertEquals(1, continued)
                prompts.start(true) { continued++ }
                assertEquals(2, continued)
                assertFalse(second.isShowing)
            }
        } finally { main { shown?.dismiss() } }
    }

    @Test fun htmlExportSkipsAttachmentPromptAndDecliningHtmlPersistsFalse() {
        val stored = mutableMapOf<String, Boolean>()
        val preferences = ExportPreferences(stored::get) { key, value -> stored[key] = value }
        var shown: MessageDialog? = null
        var continued = 0
        val prompts = ExportPrompts(activity, preferences, { next ->
            shown = next
            next.showWithin(activity.resources.displayMetrics.widthPixels)
        }, { true })
        try {
            main {
                prompts.start(false) { continued++ }
                click(shown!!, R.string.settings_export_warning_enable)
                assertEquals(1, continued)
                assertFalse(preferences.htmlExportWarningAcknowledged())
                preferences.setIncludeHtmlWithLogs(true)
                prompts.start(true) { continued++ }
                click(shown!!, R.string.settings_log_html_exclude)
                assertFalse(preferences.includeHtmlWithLogs())
                assertTrue(preferences.htmlExportWarningAcknowledged())
                assertEquals(2, continued)
            }
        } finally { main { shown?.dismiss() } }
    }

    private fun click(dialog: MessageDialog, resource: Int) {
        dialog.window!!.decorView.allViews.single { it.contentDescription == activity.getString(resource) }.performClick()
    }

    private fun assertBluePath(dialog: MessageDialog, expected: String) {
        val text = dialog.window!!.decorView.allViews.filterIsInstance<TextView>()
            .map { it.text }.filterIsInstance<Spanned>().single { it.toString().contains(expected) }
        val blue = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .single { it.foregroundColor == SettingsComponentColors.SELECTED }
        assertEquals(expected, text.subSequence(text.getSpanStart(blue), text.getSpanEnd(blue)).toString())
    }
}
