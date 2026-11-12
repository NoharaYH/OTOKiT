package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeMenuGroupsTest {
    private fun theme(game: GameLine, id: String) =
        ThemeOption.Html(ThemeRegistry.defaultFor(game).copy(id = id))

    @Test fun mergedListKeepsUniversalAndBothGameGroupEnds() {
        val options = listOf(ThemeOptions.placeholder,
            theme(GameLine.Mai, "mai-new-1"), theme(GameLine.Mai, "mai-new-2"),
            theme(GameLine.Chu, "chu-new-1"), theme(GameLine.Chu, "chu-new-2"))
        assertEquals(listOf(ThemeMenuEdge(true, true), ThemeMenuEdge(true, false),
            ThemeMenuEdge(false, true), ThemeMenuEdge(true, false), ThemeMenuEdge(false, true)),
            themeMenuEdges(options))
    }

    @Test fun excludingSelectedFirstOrLastTransfersEdgesToRemainingItems() {
        val options = listOf(ThemeOptions.placeholder, theme(GameLine.Mai, "first"),
            theme(GameLine.Mai, "middle"), theme(GameLine.Mai, "last"))
        assertEquals(ThemeMenuEdge(true, false), themeMenuEdges(options.filterNot { it.id == "first" })[1])
        assertEquals(ThemeMenuEdge(false, true), themeMenuEdges(options.filterNot { it.id == "last" }).last())
        assertEquals(ThemeMenuEdge(true, false), themeMenuEdges(options.drop(1)).first())
    }

    @Test fun singleRemainingThemeHasBothEdgesAndEmptyListIsValid() {
        assertEquals(listOf(ThemeMenuEdge(true, true)), themeMenuEdges(listOf(theme(GameLine.Chu, "future"))))
        assertTrue(themeMenuEdges(emptyList()).isEmpty())
    }
}
