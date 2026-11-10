package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.shell.navigation.Destination
import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TitleBarStateFactoryTest {

    private val maiColor = 0xFF477FE5.toInt()
    private val chuColor = 0xFF6F8CFF.toInt()

    private fun state(
        destination: Destination = Destination.ScoreSync,
        currentGame: GameLine = GameLine.Mai,
        settingsOpen: Boolean = false,
        menuEnabled: Boolean = false,
    ): TitleBarState = TitleBarStateFactory.create(
        destination = destination,
        currentGame = currentGame,
        settingsOpen = settingsOpen,
        menuEnabled = menuEnabled,
        gameFrame = null,
        navDirection = 0,
        maiTextColor = maiColor,
        chuTextColor = chuColor,
        themeCircleColor = maiColor,
        maiDecorColor = maiColor,
        chuDecorColor = chuColor,
    )

    /** 设置打开时使用固定深色标题与返回语义，但保留功能标题供退出/预览。 */
    @Test
    fun settingsOverrideFunctionalTitle() {
        val state = state(settingsOpen = true)

        assertEquals(TitleTexts.Setting, state.settingTitle)
        assertEquals(SETTINGS_TITLE_COLOR, state.settingTextColor)
        assertEquals(TitleBarPrimaryAction.Back, state.primaryAction)
        assertTrue(state.primaryEnabled)
        assertEquals(TitleTexts.forDestination(Destination.ScoreSync, GameLine.Mai), state.maiTitle)
    }

    /** 设置标题不随 destination 或当前音游变化。 */
    @Test
    fun settingsIgnoreDestinationAndGame() {
        val first = state(destination = Destination.ScoreSync, currentGame = GameLine.Mai, settingsOpen = true)
        val second = state(destination = Destination.MusicData, currentGame = GameLine.Chu, settingsOpen = true)

        assertEquals(first.settingTitle, second.settingTitle)
        assertEquals(first.settingTextColor, second.settingTextColor)
        assertEquals(TitleBarPrimaryAction.Back, second.primaryAction)
    }

    /** 功能态同时提供 Mai/Chu 标题；菜单许可由窗口形态决定。 */
    @Test
    fun functionalCarriesBothGameTitlesAndMenuPermission() {
        val enabled = state(destination = Destination.MusicData, currentGame = GameLine.Chu, menuEnabled = true)
        val disabled = state(destination = Destination.MusicData, currentGame = GameLine.Chu, menuEnabled = false)

        assertEquals(TitleTexts.forDestination(Destination.MusicData, GameLine.Mai), enabled.maiTitle)
        assertEquals(TitleTexts.forDestination(Destination.MusicData, GameLine.Chu), enabled.chuTitle)
        assertEquals(maiColor, enabled.maiTextColor)
        assertEquals(chuColor, enabled.chuTextColor)
        assertEquals(TitleBarPrimaryAction.Menu, enabled.primaryAction)
        assertTrue(enabled.primaryEnabled)
        assertFalse(disabled.primaryEnabled)
        assertEquals(maiColor, enabled.maiDecorColor)
        assertEquals(chuColor, enabled.chuDecorColor)
    }

    /** 设置态不把功能文案/颜色写进设置标题。 */
    @Test
    fun settingsUseFixedTitleColor() {
        val state = state(destination = Destination.ComingSoon, settingsOpen = true)

        assertEquals(SETTINGS_TITLE_COLOR, state.settingTextColor)
        assertEquals(TitleTexts.Setting, state.settingTitle)
    }
}
