package com.otokit.noharayh.ui.shell.selection

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.RegisteredTheme
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.theme.ThemeDefinition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSelectionControllerTest {

    private val maiA = theme("mai-a", GameLine.Mai)
    private val maiB = theme("mai-b", GameLine.Mai)
    private val chuA = theme("chu-a", GameLine.Chu)
    private val chuB = theme("chu-b", GameLine.Chu)

    private fun initialState(): GameSelectionState =
        GameSelectionState(currentGame = GameLine.Mai, maiThemeId = "mai-a", chuThemeId = "chu-a")

    /** 同值选择音游不产生新的稳定变化。 */
    @Test
    fun selectGameDeduplicates() {
        val controller = GameSelectionController(initialState())

        assertFalse(controller.selectGame(GameLine.Mai))
        assertEquals(initialState(), controller.state)

        assertTrue(controller.selectGame(GameLine.Chu))
        assertEquals(GameLine.Chu, controller.state.currentGame)
        assertEquals("mai-a", controller.state.maiThemeId)
    }

    /** 选择当前音游的另一主题：只更新该 ID。 */
    @Test
    fun selectThemeUpdatesMatchingGameLine() {
        val controller = GameSelectionController(initialState())

        assertTrue(controller.selectTheme(maiB))

        assertEquals("mai-b", controller.state.maiThemeId)
        assertEquals("chu-a", controller.state.chuThemeId)
        assertEquals(GameLine.Mai, controller.state.currentGame)
    }

    /** 改变非当前音游的主题不改变 currentGame。 */
    @Test
    fun selectThemeForOtherGameKeepsCurrentGame() {
        val controller = GameSelectionController(initialState())

        assertTrue(controller.selectTheme(chuB))

        assertEquals(GameLine.Mai, controller.state.currentGame)
        assertEquals("chu-b", controller.state.chuThemeId)
        assertEquals("mai-a", controller.state.maiThemeId)
    }

    /** 同主题重复选择是无操作。 */
    @Test
    fun selectThemeDeduplicates() {
        val controller = GameSelectionController(initialState())

        assertFalse(controller.selectTheme(maiA))
        assertFalse(controller.selectTheme(chuA))
        assertEquals(initialState(), controller.state)
    }

    /** selectedThemeId 按当前音游取值。 */
    @Test
    fun selectedThemeIdFollowsCurrentGame() {
        val controller = GameSelectionController(initialState())
        assertEquals("mai-a", controller.state.selectedThemeId())

        controller.selectGame(GameLine.Chu)

        assertEquals("chu-a", controller.state.selectedThemeId())
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
