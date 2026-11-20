package com.otokit.noharayh.ui.settings.components

import android.content.Context
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.otokit.noharayh.R
import com.otokit.noharayh.preferences.ExportPreferences
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialogAction

/** 两种真实导出入口共用的首次选择；窗口生命周期仍由所属页面管理。 */
internal class ExportPrompts(
    private val context: Context,
    private val preferences: ExportPreferences,
    private val show: (MessageDialog) -> Unit,
    private val canContinue: () -> Boolean,
) {
    fun start(forLog: Boolean, proceed: () -> Unit) {
        if (preferences.exportWarningAcknowledged()) {
            askHtml(forLog, proceed)
            return
        }
        fun choose(enabled: Boolean) {
            if (!canContinue()) return
            preferences.setRedactExports(enabled)
            preferences.acknowledgeExportWarning()
            askHtml(forLog, proceed)
        }
        show(MessageDialog(context, R.drawable.ic_error_40, message(R.string.settings_export_warning),
            context.getString(R.string.settings_export_warning_enable), null,
            additionalAction = MessageDialogAction(context.getString(R.string.settings_export_warning_disable),
                SettingsComponentColors.TEXT) { choose(false) }, cancelOnTouchOutside = false) { choose(true) })
    }

    private fun askHtml(forLog: Boolean, proceed: () -> Unit) {
        if (!canContinue()) return
        if (!forLog || preferences.htmlExportWarningAcknowledged()) {
            proceed()
            return
        }
        fun choose(enabled: Boolean) {
            if (!canContinue()) return
            preferences.setIncludeHtmlWithLogs(enabled)
            preferences.acknowledgeHtmlExportWarning()
            proceed()
        }
        show(MessageDialog(context, R.drawable.ic_info_outline_24, message(R.string.settings_log_html_warning),
            context.getString(R.string.settings_log_html_include), null,
            accentColor = SettingsComponentColors.SELECTED,
            additionalAction = MessageDialogAction(context.getString(R.string.settings_log_html_exclude),
                SettingsComponentColors.TEXT) { choose(false) }, cancelOnTouchOutside = false) { choose(true) })
    }

    private fun message(resource: Int): CharSequence = SpannableString(context.getText(resource)).apply {
        getSpans(0, length, Annotation::class.java).filter { it.key == "color" }.forEach {
            val color = when (it.value) {
                "warning" -> SettingsComponentColors.WARNING
                "link" -> SettingsComponentColors.SELECTED
                else -> return@forEach
            }
            setSpan(ForegroundColorSpan(color), getSpanStart(it), getSpanEnd(it), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
