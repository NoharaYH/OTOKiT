package com.otokit.noharayh.ui.shell.background

import com.otokit.noharayh.ui.theme.GameLine

/** 背景瞬时阶段：Idle 只表示视觉结束，选择提交由 shell 在选中回调中完成。 */
enum class BackgroundPhase { Idle, Dragging, Settling }

/**
 * 背景对外只读快照。
 *
 * selectedGame 是 shell 已提交选择的镜像；progress 为 Mai(0) → Chu(1) 的归一化进度。
 */
data class BackgroundSnapshot(
    val selectedGame: GameLine,
    val progress: Float,
    val phase: BackgroundPhase,
)

/**
 * 两页与音游的显式映射。
 *
 * 页面身份固定为 0 = Mai、1 = Chu；进度取 `(position + offset)` 并夹到 0—1，
 * 不随手势方向倒置，也不用 ordinal 代替视觉位置。
 */
object BackgroundPagerMapping {

    const val PAGE_MAI: Int = 0
    const val PAGE_CHU: Int = 1

    fun pageFor(gameLine: GameLine): Int = when (gameLine) {
        GameLine.Mai -> PAGE_MAI
        GameLine.Chu -> PAGE_CHU
    }

    fun gameForPage(page: Int): GameLine? = when (page) {
        PAGE_MAI -> GameLine.Mai
        PAGE_CHU -> GameLine.Chu
        else -> null
    }

    fun progress(page: Int, pageOffset: Float): Float = (page + pageOffset).coerceIn(0f, 1f)

    fun endpointProgress(gameLine: GameLine): Float = pageFor(gameLine).toFloat()
}
