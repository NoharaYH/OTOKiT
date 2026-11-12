package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition
import com.otokit.noharayh.ui.theme.ThemeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSelectionControllerTest {

    private val maiA = theme("mai-a", GameLine.Mai)
    private val maiB = theme("mai-b", GameLine.Mai)
    private val chuA = theme("chu-a", GameLine.Chu)
    private val chuB = theme("chu-b", GameLine.Chu)
    private val registeredThemes = listOf(maiA, maiB, chuA, chuB)

    private fun initialState(): GameSelectionState =
        GameSelectionState(currentGame = GameLine.Mai, maiThemeId = "mai-a", chuThemeId = "chu-a")

    private fun controller(): GameSelectionController = GameSelectionController(
        initialState = initialState(),
        lookup = { id -> registeredThemes.firstOrNull { it.id == id } },
    )

    /** 同值选择音游不产生新的稳定变化。 */
    @Test
    fun selectGameDeduplicates() {
        val controller = controller()

        assertFalse(controller.selectGame(GameLine.Mai))
        assertEquals(initialState(), controller.state)

        assertTrue(controller.selectGame(GameLine.Chu))
        assertEquals(GameLine.Chu, controller.state.currentGame)
        assertEquals("mai-a", controller.state.maiThemeId)
    }

    /** 选择当前音游的另一主题：只更新该 ID。 */
    @Test
    fun selectThemeUpdatesMatchingGameLine() {
        val controller = controller()

        assertTrue(controller.selectTheme(maiB))

        assertEquals("mai-b", controller.state.maiThemeId)
        assertEquals("chu-a", controller.state.chuThemeId)
        assertEquals(GameLine.Mai, controller.state.currentGame)
    }

    /** 改变非当前音游的主题不改变 currentGame。 */
    @Test
    fun selectThemeForOtherGameKeepsCurrentGame() {
        val controller = controller()

        assertTrue(controller.selectTheme(chuB))

        assertEquals(GameLine.Mai, controller.state.currentGame)
        assertEquals("chu-b", controller.state.chuThemeId)
        assertEquals("mai-a", controller.state.maiThemeId)
    }

    /** 同主题重复选择是无操作。 */
    @Test
    fun selectThemeDeduplicates() {
        val controller = controller()

        assertFalse(controller.selectTheme(maiA))
        assertFalse(controller.selectTheme(chuA))
        assertEquals(initialState(), controller.state)
    }

    /** 未登记 ID 不能进入稳定状态，也不能报告需要保存的变化。 */
    @Test
    fun rejectsUnknownThemeWithoutChangingState() {
        val controller = controller()

        assertFalse(controller.selectTheme(theme("unknown", GameLine.Mai)))

        assertEquals(initialState(), controller.state)
    }

    /** 已知 ID 也不能通过修改对象中的归属写入另一音游。 */
    @Test
    fun rejectsThemeWithMismatchedOwnershipWithoutChangingState() {
        val controller = controller()

        assertFalse(controller.selectTheme(maiB.copy(gameLine = GameLine.Chu)))
        assertFalse(controller.selectTheme(chuB.copy(gameLine = GameLine.Mai)))

        assertEquals(initialState(), controller.state)
    }

    /** 默认查询连接生产 Registry，不会把测试夹具或外部构造对象自动视为登记项。 */
    @Test
    fun defaultLookupRejectsUnregisteredTheme() {
        val initial = GameSelectionState(
            currentGame = GameLine.Mai,
            maiThemeId = ThemeRegistry.defaultFor(GameLine.Mai).id,
            chuThemeId = ThemeRegistry.defaultFor(GameLine.Chu).id,
        )
        val controller = GameSelectionController(initial)

        assertFalse(controller.selectTheme(maiB))

        assertEquals(initial, controller.state)
    }

    /** selectedThemeId 按当前音游取值。 */
    @Test
    fun selectedThemeIdFollowsCurrentGame() {
        val controller = controller()
        assertEquals("mai-a", controller.state.selectedThemeId())

        controller.selectGame(GameLine.Chu)

        assertEquals("chu-a", controller.state.selectedThemeId())
    }

    @Test fun modeRoundTripPreservesIndependentAndMergedSelections() {
        val controller = controller()
        assertTrue(controller.selectTheme(maiB))
        assertTrue(controller.selectThemeMode(ThemeSelectionMode.Merged))
        assertTrue(controller.selectOption(ThemeOption.Html(chuB)))
        assertEquals("mai-b", controller.state.maiThemeId)
        assertEquals("chu-a", controller.state.chuThemeId)
        assertTrue(controller.selectThemeMode(ThemeSelectionMode.None))
        assertFalse(controller.selectOption(ThemeOptions.placeholder, GameLine.Mai))
        assertTrue(controller.selectThemeMode(ThemeSelectionMode.Independent))
        assertEquals("mai-b", controller.state.maiThemeId)
        assertEquals("chu-b", controller.state.mergedThemeId)
        assertTrue(controller.selectOption(ThemeOptions.placeholder, GameLine.Chu))
        assertEquals(ThemeOptions.PLACEHOLDER_ID, controller.state.chuThemeId)
    }

    @Test fun mergedModeRejectsUnknownIdsAndForgedOwnership() {
        val controller = controller()
        controller.selectThemeMode(ThemeSelectionMode.Merged)
        val before = controller.state
        assertFalse(controller.selectOption(ThemeOption.Html(theme("missing", GameLine.Mai))))
        assertFalse(controller.selectOption(ThemeOption.Html(maiA.copy(gameLine = GameLine.Chu))))
        assertFalse(controller.selectOption(ThemeOption.Placeholder("fake", "fake")))
        assertEquals(before, controller.state)
    }

    private fun theme(id: String, gameLine: GameLine): RegisteredTheme = RegisteredTheme(
        id = id,
        displayName = id,
        gameLine = gameLine,
        definition = ThemeDefinition(
            htmlAssetPath = "background/$id/index.html",
            colors = ThemeColors(basicColor = 0, buttonColor = 0),
        ),
    )
}
