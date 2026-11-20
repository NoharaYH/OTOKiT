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

    private companion object {
        const val REDACT = "redact_exports"
        const val WARNING = "export_warning_acknowledged"
    }
}
