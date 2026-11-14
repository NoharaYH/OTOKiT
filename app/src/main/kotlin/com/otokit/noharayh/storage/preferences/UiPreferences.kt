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
    val backgroundHardwareAcceleration: Boolean? = null,
    val backgroundHtmlAnimations: Boolean? = null,
    val noneThemeSeedColor: Int? = null,
    val noneThemeTintBackground: Boolean? = null,
    val noneThemeUseWallpaper: Boolean? = null,
    val noneThemePrimaryColor: Int? = null,
    val noneThemeSecondaryColor: Int? = null,
    val noneThemeWallpaperSecondary: Int? = null,
)

/** 最小 UI 偏好读写入口：只读写原始字段。 */
interface UiPreferences {

    fun readSelection(): UiPreferenceRecord

    /** 两个背景选项一次写入；保留音游和主题字段。 */
    fun writeBackgroundRendering(hardwareAccelerated: Boolean, htmlAnimations: Boolean)

    /** 完整快照必须一次写入，所有字段均为必填；实现不得静默丢弃模式字段。 */
    fun writeSelection(
        version: Int,
        currentGame: String,
        maiThemeId: String,
        chuThemeId: String,
        themeMode: String,
        mergedThemeId: String,
        noneThemeSeedColor: Int,
        noneThemeTintBackground: Boolean,
        noneThemeUseWallpaper: Boolean,
        noneThemePrimaryColor: Int?,
        noneThemeSecondaryColor: Int?,
        noneThemeWallpaperSecondary: Int?,
    )
}
