package com.otokit.noharayh.preferences

/** 本机导出选择与首次敏感数据提醒；不参与账号或云同步。 */
class ExportPreferences(
    private val readBoolean: (String) -> Boolean?,
    private val writeBoolean: (String, Boolean) -> Unit,
) {
    fun redactExports(): Boolean = readBoolean(REDACT) ?: true
    fun setRedactExports(enabled: Boolean) { writeBoolean(REDACT, enabled) }
    fun exportWarningAcknowledged(): Boolean = readBoolean(WARNING) ?: false
    fun acknowledgeExportWarning() { writeBoolean(WARNING, true) }
    fun includeHtmlWithLogs(): Boolean = readBoolean(HTML) ?: false
    fun setIncludeHtmlWithLogs(enabled: Boolean) { writeBoolean(HTML, enabled) }
    fun htmlExportWarningAcknowledged(): Boolean = readBoolean(HTML_WARNING) ?: false
    fun acknowledgeHtmlExportWarning() { writeBoolean(HTML_WARNING, true) }

    private companion object {
        const val REDACT = "redact_exports"
        const val WARNING = "export_warning_acknowledged"
        const val HTML = "include_html_with_logs"
        const val HTML_WARNING = "log_html_warning_acknowledged"
    }
}
