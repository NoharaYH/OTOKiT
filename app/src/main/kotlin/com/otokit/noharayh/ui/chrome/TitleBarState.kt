package com.otokit.noharayh.ui.chrome

/** 标题栏主按钮语义：功能态是菜单（07 才有消费者），设置态是返回。 */
enum class TitleBarPrimaryAction { Menu, Back }

/**
 * 标题栏展示状态：只含纯数据，不含控制器、主题对象或 View。
 */
data class TitleBarState(
    val text: TitleText,
    val textColor: Int,
    val circleColor: Int,
    val maiDecorColor: Int,
    val chuDecorColor: Int,
    val primaryAction: TitleBarPrimaryAction,
    val primaryEnabled: Boolean,
)

/** 设置标题的固定文字色，来自旧 `PhoneSize.SETTINGS_TITLE_COLOR`。 */
const val SETTINGS_TITLE_COLOR: Int = 0xFF1A1A1A.toInt()
