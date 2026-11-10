package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.theme.GameLine

/** 双音游展示阶段：Idle 必须有唯一已提交端点。 */
enum class GamePhase { Idle, Dragging, Settling }

/**
 * 页面/标题共用的纯展示帧。
 *
 * 不持有背景宿主、Pager 或导航状态；窗口尺寸是 Application 窗口，不是物理屏幕。
 */
data class GameFrame(
    val selectedGame: GameLine,
    val progress: Float,
    val phase: GamePhase,
    val windowWidthPx: Int,
    val contentWidthPx: Int,
    val contentHeightPx: Int,
) {
    val normalizedProgress: Float
        get() = when (phase) {
            GamePhase.Idle -> if (selectedGame == GameLine.Mai) 0f else 1f
            GamePhase.Dragging, GamePhase.Settling -> progress.coerceIn(0f, 1f)
        }
}
