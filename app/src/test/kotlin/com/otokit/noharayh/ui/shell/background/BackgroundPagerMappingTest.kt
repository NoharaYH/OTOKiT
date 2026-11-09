package com.otokit.noharayh.ui.shell.background

import com.otokit.noharayh.ui.theme.GameLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundPagerMappingTest {

    /** 页面身份固定为 0 = Mai、1 = Chu，越界索引不产生音游。 */
    @Test
    fun mapsPagesExplicitly() {
        assertEquals(0, BackgroundPagerMapping.pageFor(GameLine.Mai))
        assertEquals(1, BackgroundPagerMapping.pageFor(GameLine.Chu))
        assertEquals(GameLine.Mai, BackgroundPagerMapping.gameForPage(0))
        assertEquals(GameLine.Chu, BackgroundPagerMapping.gameForPage(1))
        assertNull(BackgroundPagerMapping.gameForPage(-1))
        assertNull(BackgroundPagerMapping.gameForPage(2))
    }

    /** 进度取 (position + offset) 并夹到 0—1，不随手势方向倒置。 */
    @Test
    fun computesNormalizedProgress() {
        assertEquals(0f, BackgroundPagerMapping.progress(0, 0f), 0f)
        assertEquals(0.25f, BackgroundPagerMapping.progress(0, 0.25f), 0f)
        assertEquals(0.7f, BackgroundPagerMapping.progress(0, 0.7f), 0f)
        assertEquals(1f, BackgroundPagerMapping.progress(1, 0f), 0f)
        assertEquals(1f, BackgroundPagerMapping.progress(1, 0.5f), 0f)
        assertEquals(0f, BackgroundPagerMapping.progress(0, -0.2f), 0f)
    }

    /** Idle 端点固定为 Mai = 0、Chu = 1。 */
    @Test
    fun exposesIdleEndpoints() {
        assertEquals(0f, BackgroundPagerMapping.endpointProgress(GameLine.Mai), 0f)
        assertEquals(1f, BackgroundPagerMapping.endpointProgress(GameLine.Chu), 0f)
    }
}
