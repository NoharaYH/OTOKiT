package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GameSelectionRestoreTest {
    @Test
    fun restoresNonePaletteFieldsIndependentlyAndKeepsOldRecordsUsable() {
        val preferences = SelectionSource(1, "chu", "mai-a", "chu-a",
            noneThemeSeedColor = 0xFF008577.toInt(), noneThemeTintBackground = true)
        val saved = SelectionSource(1, null, null, null,
            noneThemeSeedColor = 0x006750A4, noneThemeTintBackground = false)
        val restored = restoreGameSelection(saved, preferences, lookup, defaultFor)
        assertEquals(0xFF008577.toInt(), restored.noneThemeSeedColor)
        assertEquals(false, restored.noneThemeTintBackground)
        assertEquals(GameLine.Chu, restored.currentGame)

        val legacy = restoreGameSelection(SelectionSource(1, "chu", "mai-b", "chu-b"),
            null, lookup, defaultFor)
        assertEquals(com.otokit.noharayh.ui.theme.NoneThemePalette.DEFAULT_SEED_COLOR, legacy.noneThemeSeedColor)
        assertEquals(false, legacy.noneThemeTintBackground)
        assertEquals("mai-b", legacy.maiThemeId)
        assertEquals("chu-b", legacy.chuThemeId)
    }

    @org.junit.Test fun restoresMergedAndNoneWithPlaceholderAcrossSavedState() {
        val registry = com.otokit.noharayh.ui.theme.ThemeRegistry
        val initial = GameSelectionState(
            com.otokit.noharayh.ui.theme.GameLine.Chu,
            com.otokit.noharayh.ui.theme.ThemeOptions.PLACEHOLDER_ID,
            registry.defaultFor(com.otokit.noharayh.ui.theme.GameLine.Chu).id,
            com.otokit.noharayh.ui.theme.ThemeSelectionMode.None,
            registry.defaultFor(com.otokit.noharayh.ui.theme.GameLine.Mai).id,
            noneThemeSeedColor = 0xFF006B54.toInt(),
            noneThemeTintBackground = true,
        )
        org.junit.Assert.assertEquals(initial, restoreGameSelection(
            GameSelectionFormat.encode(initial), null, registry::findById, registry::defaultFor,
        ))
    }


    private val maiA = theme("mai-a", GameLine.Mai)
    private val maiB = theme("mai-b", GameLine.Mai)
    private val chuA = theme("chu-a", GameLine.Chu)
    private val chuB = theme("chu-b", GameLine.Chu)

    private val lookup: (String?) -> RegisteredTheme? = { id ->
        listOf(maiA, maiB, chuA, chuB).firstOrNull { it.id == id }
    }

    private val defaultFor: (GameLine) -> RegisteredTheme = { gameLine ->
        if (gameLine == GameLine.Mai) maiA else chuA
    }

    /** 编码只含稳定字段：版本 1、音游字符串、规范主题 ID。 */
    @Test
    fun encodesStableFields() {
        val fields = GameSelectionFormat.encode(GameSelectionState(GameLine.Chu, "mai-a", "chu-a"))

        assertEquals(1, fields.version)
        assertEquals("chu", fields.currentGame)
        assertEquals("mai-a", fields.maiThemeId)
        assertEquals("chu-a", fields.chuThemeId)
        assertEquals("mai", GameSelectionFormat.gameValue(GameLine.Mai))
        assertEquals("chu", GameSelectionFormat.gameValue(GameLine.Chu))
    }

    /** 音游只接受显式字符串；ordinal、展示名与大小写变体都拒绝。 */
    @Test
    fun parsesOnlyExplicitGameStrings() {
        assertEquals(GameLine.Mai, GameSelectionFormat.parseGame("mai"))
        assertEquals(GameLine.Chu, GameSelectionFormat.parseGame("chu"))

        for (value in listOf(null, "", " ", "0", "1", "Mai", "CHU", "CiRCLE+", "mai-circle-plus")) {
            assertNull("不应解析：$value", GameSelectionFormat.parseGame(value))
        }
    }

    /** 版本缺失或不支持时整份来源不可用。 */
    @Test
    fun rejectsUnsupportedVersions() {
        assertNull(GameSelectionFormat.usable(source(version = null)))
        assertNull(GameSelectionFormat.usable(source(version = 0)))
        assertNull(GameSelectionFormat.usable(source(version = 2)))
        assertNotNull(GameSelectionFormat.usable(source(version = 1)))
    }

    /** 保存状态优先于偏好，按字段整体生效。 */
    @Test
    fun savedStateWinsOverPreferences() {
        val restored = restoreGameSelection(
            saved = source(game = "chu", maiId = "mai-a", chuId = "chu-a"),
            preferences = source(game = "mai", maiId = "mai-b", chuId = "chu-b"),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Chu, restored.currentGame)
        assertEquals("mai-a", restored.maiThemeId)
        assertEquals("chu-a", restored.chuThemeId)
    }

    /** 保存状态缺失的字段由偏好逐项补齐。 */
    @Test
    fun preferencesFillMissingFields() {
        val restored = restoreGameSelection(
            saved = source(game = null, maiId = null, chuId = "chu-a"),
            preferences = source(game = "chu", maiId = "mai-b", chuId = null),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Chu, restored.currentGame)
        assertEquals("mai-b", restored.maiThemeId)
        assertEquals("chu-a", restored.chuThemeId)
    }

    /** 坏掉的保存音游不能从合法主题 ID 反推。 */
    @Test
    fun badSavedGameDoesNotInferFromThemes() {
        val restored = restoreGameSelection(
            saved = source(game = "unknown", maiId = "mai-a", chuId = "chu-a"),
            preferences = source(game = "chu", maiId = null, chuId = null),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Chu, restored.currentGame)
    }

    /** 保存状态里错属或未知的主题不能遮住偏好里的合法值。 */
    @Test
    fun badSavedThemeDoesNotMaskValidPreference() {
        val restored = restoreGameSelection(
            saved = source(game = "mai", maiId = "chu-a", chuId = "unknown"),
            preferences = source(game = "mai", maiId = "mai-b", chuId = "chu-b"),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals("mai-b", restored.maiThemeId)
        assertEquals("chu-b", restored.chuThemeId)
    }

    /** 来源版本错误时整份来源被忽略，另一来源仍生效。 */
    @Test
    fun ignoresSourceWithUnsupportedVersion() {
        val restored = restoreGameSelection(
            saved = source(version = 2, game = "chu", maiId = "mai-b", chuId = "chu-b"),
            preferences = source(version = 1, game = "mai", maiId = "mai-a", chuId = "chu-a"),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Mai, restored.currentGame)
        assertEquals("mai-a", restored.maiThemeId)
        assertEquals("chu-a", restored.chuThemeId)
    }

    /** 两个来源都不可用时才回退到 Registry 默认。 */
    @Test
    fun fallsBackToDefaultsWhenNothingValid() {
        val restored = restoreGameSelection(
            saved = null,
            preferences = source(version = null, game = null, maiId = null, chuId = null),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Mai, restored.currentGame)
        assertEquals("mai-a", restored.maiThemeId)
        assertEquals("chu-a", restored.chuThemeId)
    }

    /** 各字段独立判断：一个坏值不影响另一个字段的来源选择。 */
    @Test
    fun keepsFieldsIndependent() {
        val restored = restoreGameSelection(
            saved = source(game = "mai", maiId = "mai-a", chuId = "bad-chu"),
            preferences = source(game = "chu", maiId = "bad-mai", chuId = "chu-b"),
            lookup = lookup,
            defaultFor = defaultFor,
        )

        assertEquals(GameLine.Mai, restored.currentGame)
        assertEquals("mai-a", restored.maiThemeId)
        assertEquals("chu-b", restored.chuThemeId)
    }

    /** 任意组合恢复出的 ID 都必须已登记且归属正确。 */
    @Test
    fun restoredIdsAreAlwaysRegisteredAndOwned() {
        val cases = listOf(
            source(game = "chu", maiId = "unknown", chuId = "unknown"),
            source(game = null, maiId = "chu-a", chuId = "mai-a"),
            source(version = 9, game = "chu", maiId = "mai-a", chuId = "chu-a"),
            null,
        )

        for (saved in cases) {
            val restored = restoreGameSelection(saved, null, lookup, defaultFor)
            assertEquals(GameLine.Mai, lookup(restored.maiThemeId)?.gameLine)
            assertEquals(GameLine.Chu, lookup(restored.chuThemeId)?.gameLine)
        }
    }

    private fun source(
        version: Int? = 1,
        game: String? = "mai",
        maiId: String? = "mai-a",
        chuId: String? = "chu-a",
    ): SelectionSource = SelectionSource(
        version = version,
        currentGame = game,
        maiThemeId = maiId,
        chuThemeId = chuId,
    )

    private fun theme(id: String, gameLine: GameLine): RegisteredTheme = RegisteredTheme(
        id = id,
        displayName = id,
        gameLine = gameLine,
        definition = ThemeDefinition(
            htmlAssetPath = "background/$id/index.html",
            colors = ThemeColors(primaryColor = 0, secondaryColor = 0),
        ),
    )
}
