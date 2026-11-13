package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.NoneThemePalette
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode

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
    val themeMode: ThemeSelectionMode = ThemeSelectionMode.Independent,
    val mergedThemeId: String = ThemeOptions.PLACEHOLDER_ID,
    val noneThemeSeedColor: Int = NoneThemePalette.DEFAULT_SEED_COLOR,
    val noneThemeTintBackground: Boolean = false,
    val noneThemeUseWallpaper: Boolean = true,
    val noneThemePrimaryColor: Int? = null,
    val noneThemeSecondaryColor: Int? = null,
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
    val themeMode: String? = null,
    val mergedThemeId: String? = null,
    val noneThemeSeedColor: Int? = null,
    val noneThemeTintBackground: Boolean? = null,
    val noneThemeUseWallpaper: Boolean? = null,
    val noneThemePrimaryColor: Int? = null,
    val noneThemeSecondaryColor: Int? = null,
)

/** 稳定字符串编码：音游用 `mai` / `chu`，主题用规范 ID，版本固定为 1。 */
object GameSelectionFormat {

    const val VERSION: Int = 1
    const val GAME_MAI: String = "mai"
    const val GAME_CHU: String = "chu"
    const val MODE_INDEPENDENT: String = "independent"
    const val MODE_MERGED: String = "merged"
    const val MODE_NONE: String = "none"

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

    fun modeValue(mode: ThemeSelectionMode): String = when (mode) {
        ThemeSelectionMode.Independent -> MODE_INDEPENDENT
        ThemeSelectionMode.Merged -> MODE_MERGED
        ThemeSelectionMode.None -> MODE_NONE
    }

    fun parseMode(value: String?): ThemeSelectionMode? = when (value) {
        MODE_INDEPENDENT -> ThemeSelectionMode.Independent
        MODE_MERGED -> ThemeSelectionMode.Merged
        MODE_NONE -> ThemeSelectionMode.None
        else -> null
    }

    fun encode(state: GameSelectionState): SelectionSource = SelectionSource(
        version = VERSION,
        currentGame = gameValue(state.currentGame),
        maiThemeId = state.maiThemeId,
        chuThemeId = state.chuThemeId,
        themeMode = modeValue(state.themeMode),
        mergedThemeId = state.mergedThemeId,
        noneThemeSeedColor = state.noneThemeSeedColor,
        noneThemeTintBackground = state.noneThemeTintBackground,
        noneThemeUseWallpaper = state.noneThemeUseWallpaper,
        noneThemePrimaryColor = state.noneThemePrimaryColor,
        noneThemeSecondaryColor = state.noneThemeSecondaryColor,
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

    val themeMode = GameSelectionFormat.parseMode(savedFields?.themeMode)
        ?: GameSelectionFormat.parseMode(preferenceFields?.themeMode)
        ?: ThemeSelectionMode.Independent

    return GameSelectionState(
        currentGame = currentGame,
        maiThemeId = resolveThemeId(GameLine.Mai, savedFields?.maiThemeId, preferenceFields?.maiThemeId, lookup, defaultFor),
        chuThemeId = resolveThemeId(GameLine.Chu, savedFields?.chuThemeId, preferenceFields?.chuThemeId, lookup, defaultFor),
        themeMode = themeMode,
        mergedThemeId = resolveMergedId(
            savedFields?.mergedThemeId,
            preferenceFields?.mergedThemeId,
            lookup,
        ),
        noneThemeSeedColor = savedFields?.noneThemeSeedColor?.takeIf { it ushr 24 == 0xFF }
            ?: preferenceFields?.noneThemeSeedColor?.takeIf { it ushr 24 == 0xFF }
            ?: NoneThemePalette.DEFAULT_SEED_COLOR,
        noneThemeTintBackground = savedFields?.noneThemeTintBackground
            ?: preferenceFields?.noneThemeTintBackground ?: false,
        noneThemeUseWallpaper = savedFields?.noneThemeUseWallpaper
            ?: preferenceFields?.noneThemeUseWallpaper ?: true,
        noneThemePrimaryColor = savedFields?.noneThemePrimaryColor?.takeIf { it ushr 24 == 0xFF }
            ?: preferenceFields?.noneThemePrimaryColor?.takeIf { it ushr 24 == 0xFF },
        noneThemeSecondaryColor = savedFields?.noneThemeSecondaryColor?.takeIf { it ushr 24 == 0xFF }
            ?: preferenceFields?.noneThemeSecondaryColor?.takeIf { it ushr 24 == 0xFF },
    )
}

private fun resolveThemeId(
    gameLine: GameLine,
    savedId: String?,
    preferenceId: String?,
    lookup: (String?) -> RegisteredTheme?,
    defaultFor: (GameLine) -> RegisteredTheme,
): String {
    validThemeFor(gameLine, savedId, lookup)?.let { return it }
    validThemeFor(gameLine, preferenceId, lookup)?.let { return it }
    return defaultFor(gameLine).id
}

private fun validThemeFor(
    gameLine: GameLine,
    id: String?,
    lookup: (String?) -> RegisteredTheme?,
): String? {
    if (id == ThemeOptions.PLACEHOLDER_ID) return id
    return lookup(id)?.takeIf { it.gameLine == gameLine }?.id
}

private fun resolveMergedId(
    savedId: String?,
    preferenceId: String?,
    lookup: (String?) -> RegisteredTheme?,
): String {
    validMergedId(savedId, lookup)?.let { return it }
    validMergedId(preferenceId, lookup)?.let { return it }
    return ThemeOptions.PLACEHOLDER_ID
}

private fun validMergedId(
    id: String?,
    lookup: (String?) -> RegisteredTheme?,
): String? {
    if (id == ThemeOptions.PLACEHOLDER_ID) return id
    return lookup(id)?.id
}
