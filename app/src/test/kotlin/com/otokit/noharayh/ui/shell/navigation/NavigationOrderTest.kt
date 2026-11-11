package com.otokit.noharayh.ui.shell.navigation

import org.junit.Assert.assertEquals
import org.junit.Test

class NavigationOrderTest {

    /** 导航方向只取自导航契约，顺序与目的地的稳定声明一致。 */
    @Test
    fun destinationsFollowStableNavigationOrder() {
        assertEquals(
            listOf(Destination.ScoreSync, Destination.MusicData, Destination.ComingSoon),
            NavigationOrder.destinations,
        )
    }

    @Test
    fun directionUsesNavigationOrderNotPresentationOrder() {
        assertEquals(1, NavigationOrder.direction(Destination.ScoreSync, Destination.MusicData))
        assertEquals(1, NavigationOrder.direction(Destination.MusicData, Destination.ComingSoon))
        assertEquals(-1, NavigationOrder.direction(Destination.ComingSoon, Destination.ScoreSync))
        assertEquals(1, NavigationOrder.direction(null, Destination.MusicData))
        assertEquals(0, NavigationOrder.direction(Destination.MusicData, Destination.MusicData))
    }
}
