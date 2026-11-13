package com.otokit.noharayh.ui.theme

/**
 * 主题所属音游。
 *
 * 只表示归属，不含 Pager 下标、窗口或业务对象；保存协议由使用方的适配器定义，不使用 ordinal。
 */
enum class GameLine { Mai, Chu }

/** 主色用于常规内容；副色仅用于单主题模式的另一标题栏三角。均为不透明 ARGB。 */
data class ThemeColors(
    val primaryColor: Int,
    val secondaryColor: Int,
)

/** 主题自身提供的描述：HTML 相对入口与颜色，不含 WebView 或加载函数。 */
data class ThemeDefinition(
    val htmlAssetPath: String,
    val colors: ThemeColors,
)

/**
 * Registry 统一登记的完整主题。
 *
 * 稳定 ID、展示名与音游归属只在注册处出现一次。
 */
data class RegisteredTheme(
    val id: String,
    val displayName: String,
    val gameLine: GameLine,
    val definition: ThemeDefinition,
)
