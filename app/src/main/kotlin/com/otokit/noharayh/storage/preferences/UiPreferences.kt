package com.otokit.noharayh.storage.preferences

/**
 * 原始 UI 偏好记录。
 *
 * 只含基础类型且允许为空：缺失、类型错误与未知值由 UI 侧的恢复规则判断，
 * storage 不解释默认音游、主题归属或恢复优先级。
 */
data class UiPreferenceRecord(
    val version: Int?,
    val currentGame: String?,
    val maiThemeId: String?,
    val chuThemeId: String?,
    val themeMode: String? = null,
    val mergedThemeId: String? = null,
)

/** 最小 UI 偏好读写入口：只读写原始字段。 */
interface UiPreferences {

    fun readSelection(): UiPreferenceRecord

    fun writeSelection(version: Int, currentGame: String, maiThemeId: String, chuThemeId: String)

    /** 主题模式单独写入；默认空实现兼容只关心旧字段的实现。 */
    fun writeThemeSelection(themeMode: String, mergedThemeId: String) = Unit
}
