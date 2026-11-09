package com.otokit.noharayh.ui.chrome

import com.otokit.noharayh.ui.shell.navigation.Destination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NavDisplayTest {

    /** 顺序沿用来源：成绩同步、歌曲详情、敬请期待。 */
    @Test
    fun keepsLegacyOrder() {
        assertEquals(
            listOf(Destination.ScoreSync, Destination.MusicData, Destination.ComingSoon),
            NavDisplay.items.map { it.destination },
        )
    }

    /** 每个 destination 都能取到对应项，不依赖 ordinal。 */
    @Test
    fun mapsEveryDestination() {
        Destination.values().forEach { destination ->
            assertEquals(destination, NavDisplay.itemFor(destination).destination)
        }
    }

    /** 三项强调色固定且互不相同，不随 Mai/Chu 主题变化。 */
    @Test
    fun accentsAreFixedAndDistinct() {
        val accents = NavDisplay.items.map { it.accentColor }
        assertEquals(accents.size, accents.toSet().size)
        assertTrue(accents.none { it == 0 })
    }
}
