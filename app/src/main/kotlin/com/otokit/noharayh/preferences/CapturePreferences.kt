package com.otokit.noharayh.preferences

/** 本机采集偏好；运行中的任务使用自己的输入快照，不订阅此值。 */
class CapturePreferences(
    private val readBoolean: (String) -> Boolean?,
    private val writeBoolean: (String, Boolean) -> Unit,
) {
    fun keepCallbackCaptureUntilLogin(): Boolean = readBoolean(KEY) ?: false
    fun setKeepCallbackCaptureUntilLogin(enabled: Boolean) { writeBoolean(KEY, enabled) }

    private companion object { const val KEY = "keep_callback_capture_until_login" }
}
