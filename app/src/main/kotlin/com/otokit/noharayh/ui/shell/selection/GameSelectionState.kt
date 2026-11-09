package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme

/**
 * 稳定的音游/主题选择，与导航状态平行。
 *
 * 进入持有者前已通过 Registry 校验；字段不可变，整体写入口在 [GameSelectionController]。
 * 不保存 scroll 进度、阶段或窗口数据。
 */
data class GameSelectionState(
    val currentGame: GameLine,
    val maiThemeId: String,
    val chuThemeId: String,
) {
    fun selectedThemeId(gameLine: GameLine = currentGame): String = when (gameLine) {
        GameLine.Mai -> maiThemeId
        GameLine.Chu -> chuThemeId
    }
}

/**
 * 单一来源的原始保存字段，可为空，不在读取阶段默认化。
 *
 * 版本缺失或不支持时整份来源不可用；各字段的类型与合法性由恢复规则逐项判断。
 */
data class SelectionSource(
    val version: Int?,
    val currentGame: String?,
    val maiThemeId: String?,
    val chuThemeId: String?,
)

/** 稳定字符串编码：音游用 `mai` / `chu`，主题用规范 ID，版本固定为 1。 */
object GameSelectionFormat {

    const val VERSION: Int = 1
    const val GAME_MAI: String = "mai"
    const val GAME_CHU: String = "chu"

    fun gameValue(gameLine: GameLine): String = when (gameLine) {
        GameLine.Mai -> GAME_MAI
        GameLine.Chu -> GAME_CHU
    }

    /** 只接受显式字符串；ordinal、展示名与大小写变体都视为未知。 */
    fun parseGame(value: String?): GameLine? = when (value) {
        GAME_MAI -> GameLine.Mai
        GAME_CHU -> GameLine.Chu
        else -> null
    }

    fun encode(state: GameSelectionState): SelectionSource = SelectionSource(
        version = VERSION,
        currentGame = gameValue(state.currentGame),
        maiThemeId = state.maiThemeId,
        chuThemeId = state.chuThemeId,
    )

    /** 版本合法才可用；缺失或不支持的版本整份来源失效。 */
    fun usable(source: SelectionSource?): SelectionSource? =
        source?.takeIf { it.version == VERSION }
}

/**
 * 按字段合并两个来源并回退到 Registry 默认。
 *
 * 每个字段独立取值：保存状态 → 偏好 → 默认；某字段无效不影响其他字段，也不从主题 ID 反推音游。
 * 候选值只用原始查询与归属校验判断，不能先 resolve 成默认项再当作“有效保存值”。
 */
fun restoreGameSelection(
    saved: SelectionSource?,
    preferences: SelectionSource?,
    lookup: (String?) -> RegisteredTheme?,
    defaultFor: (GameLine) -> RegisteredTheme,
): GameSelectionState {
    val savedFields = GameSelectionFormat.usable(saved)
    val preferenceFields = GameSelectionFormat.usable(preferences)

    val currentGame = GameSelectionFormat.parseGame(savedFields?.currentGame)
        ?: GameSelectionFormat.parseGame(preferenceFields?.currentGame)
        ?: GameLine.Mai

    return GameSelectionState(
        currentGame = currentGame,
        maiThemeId = resolveThemeId(GameLine.Mai, savedFields?.maiThemeId, preferenceFields?.maiThemeId, lookup, defaultFor),
        chuThemeId = resolveThemeId(GameLine.Chu, savedFields?.chuThemeId, preferenceFields?.chuThemeId, lookup, defaultFor),
    )
}

private fun resolveThemeId(
    gameLine: GameLine,
    savedId: String?,
    preferenceId: String?,
    lookup: (String?) -> RegisteredTheme?,
    defaultFor: (GameLine) -> RegisteredTheme,
): String {
    validThemeFor(gameLine, savedId, lookup)?.let { return it.id }
    validThemeFor(gameLine, preferenceId, lookup)?.let { return it.id }
    return defaultFor(gameLine).id
}

private fun validThemeFor(
    gameLine: GameLine,
    id: String?,
    lookup: (String?) -> RegisteredTheme?,
): RegisteredTheme? = lookup(id)?.takeIf { it.gameLine == gameLine }
