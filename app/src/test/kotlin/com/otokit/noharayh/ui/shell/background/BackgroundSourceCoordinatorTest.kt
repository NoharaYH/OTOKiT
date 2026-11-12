package com.otokit.noharayh.ui.shell.background

import com.otokit.noharayh.ui.theme.BackgroundSelection
import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.*
import org.junit.Test

class BackgroundSourceCoordinatorTest {
    private val loaded = mutableMapOf<GameLine, BackgroundSelection>()
    private val loads = mutableListOf<GameLine>()
    private val coordinator = BackgroundSourceCoordinator { game, source ->
        if (loaded[game] == source) false else {
            loaded[game] = source
            loads += game
            true
        }
    }

    @Test fun switchingBeforeFirstFrameLoadsDestinationWithoutWaitingForOldPage() {
        coordinator.render(GameLine.Mai, BackgroundSelection.Placeholder, BackgroundSelection.None)
        assertEquals(listOf(GameLine.Mai), loads)
        coordinator.select(GameLine.Chu)
        assertEquals(BackgroundSelection.None, loaded[GameLine.Chu])
        coordinator.onVisualReady(GameLine.Mai, GameLine.Chu)
        assertFalse(coordinator.isReady(GameLine.Chu))
        coordinator.onVisualReady(GameLine.Chu, GameLine.Chu)
        assertTrue(coordinator.isReady(GameLine.Chu))
        assertEquals(listOf(GameLine.Mai, GameLine.Chu), loads)
    }

    @Test fun switchingUsesLatestDeferredSourceAndInvalidatesOldReadiness() {
        coordinator.render(GameLine.Mai, BackgroundSelection.Placeholder, BackgroundSelection.Placeholder)
        coordinator.onVisualReady(GameLine.Mai, GameLine.Mai)
        coordinator.onVisualReady(GameLine.Chu, GameLine.Mai)
        coordinator.render(GameLine.Mai, BackgroundSelection.None, BackgroundSelection.None)
        coordinator.select(GameLine.Chu)
        assertEquals(BackgroundSelection.None, loaded[GameLine.Chu])
        assertFalse(coordinator.isReady(GameLine.Chu))
    }

    @Test fun currentFirstFramePreloadsOtherPageAndUnchangedSourcesDoNotReload() {
        coordinator.render(GameLine.Mai, BackgroundSelection.Placeholder, BackgroundSelection.None)
        coordinator.onVisualReady(GameLine.Mai, GameLine.Mai)
        coordinator.render(GameLine.Mai, BackgroundSelection.Placeholder, BackgroundSelection.None)
        coordinator.select(GameLine.Chu)
        assertEquals(listOf(GameLine.Mai, GameLine.Chu), loads)
        coordinator.clear()
        assertFalse(coordinator.isReady(GameLine.Mai))
    }
}
