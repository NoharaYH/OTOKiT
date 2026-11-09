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

    /** 设置打开时显示固定深色设置标题与可用的返回语义。 */
    @Test
    fun settingsOverrideFunctionalTitle() {
        val state = TitleBarStateFactory.create(
            destination = Destination.ScoreSync,
            currentGame = GameLine.Mai,
            settingsOpen = true,
            functionalTextColor = maiColor,
            themeCircleColor = maiColor,
            maiDecorColor = maiColor,
            chuDecorColor = chuColor,
        )

        assertEquals(TitleTexts.Setting, state.text)
        assertEquals(SETTINGS_TITLE_COLOR, state.textColor)
        assertEquals(TitleBarPrimaryAction.Back, state.primaryAction)
        assertTrue(state.primaryEnabled)
        assertEquals(maiColor, state.circleColor)
    }

    /** 设置标题不随 destination 或当前音游变化。 */
    @Test
    fun settingsIgnoreDestinationAndGame() {
        fun settingsState(destination: Destination, gameLine: GameLine) = TitleBarStateFactory.create(
            destination = destination,
            currentGame = gameLine,
            settingsOpen = true,
            functionalTextColor = maiColor,
            themeCircleColor = maiColor,
            maiDecorColor = maiColor,
            chuDecorColor = chuColor,
        )

        assertEquals(
            settingsState(Destination.ScoreSync, GameLine.Mai),
            settingsState(Destination.MusicData, GameLine.Chu),
        )
    }

    /** 功能态使用当前音游标题与主题色，菜单保持禁用。 */
    @Test
    fun functionalUsesThemeColorAndDisabledMenu() {
        val state = TitleBarStateFactory.create(
            destination = Destination.MusicData,
            currentGame = GameLine.Chu,
            settingsOpen = false,
            functionalTextColor = chuColor,
            themeCircleColor = chuColor,
            maiDecorColor = maiColor,
            chuDecorColor = chuColor,
        )

        assertEquals(
            TitleTexts.forDestination(Destination.MusicData, GameLine.Chu),
            state.text,
        )
        assertEquals(chuColor, state.textColor)
        assertEquals(TitleBarPrimaryAction.Menu, state.primaryAction)
        assertFalse(state.primaryEnabled)
        assertEquals(maiColor, state.maiDecorColor)
        assertEquals(chuColor, state.chuDecorColor)
    }
}
