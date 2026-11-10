package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.theme.GameLine

/** 标题栏主按钮语义：功能态是菜单（07 才有消费者），设置态是返回。 */
enum class TitleBarPrimaryAction { Menu, Back }

/**
 * 标题栏展示状态：只含纯数据，不含控制器、主题对象或 View。
 *
 * 功能态同时携带 Mai/Chu 两套标题和当前背景帧，标题栏自己决定交叉/跟手；
 * 设置态只覆盖标题文字，不改变 destination、currentGame 或导航状态。
 */
data class TitleBarState(
    val maiTitle: TitleText,
    val chuTitle: TitleText,
    val settingTitle: TitleText,
    val currentGame: GameLine,
    val gameFrame: GameFrame?,
    val navDirection: Int,
    val settingsOpen: Boolean,
    val maiTextColor: Int,
    val chuTextColor: Int,
    val settingTextColor: Int,
    val circleColor: Int,
    val maiDecorColor: Int,
    val chuDecorColor: Int,
    val primaryAction: TitleBarPrimaryAction,
    val primaryEnabled: Boolean,
)

/** 设置标题的固定文字色，来自旧 `PhoneSize.SETTINGS_TITLE_COLOR`。 */
const val SETTINGS_TITLE_COLOR: Int = 0xFF1A1A1A.toInt()
