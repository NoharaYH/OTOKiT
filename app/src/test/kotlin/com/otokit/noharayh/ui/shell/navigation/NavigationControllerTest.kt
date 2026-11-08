package com.otokit.noharayh.ui.shell.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationControllerTest {

    /** 默认显示成绩同步，设置关闭，返回不由导航消费。 */
    @Test
    fun defaultStateIsScoreSyncWithSettingsClosed() {
        val controller = NavigationController()

        assertEquals(Destination.ScoreSync, controller.state.destination)
        assertFalse(controller.state.settingsOpen)
        assertFalse(controller.canHandleBack)
    }

    /** 三个稳定标识都能成为当前目的地。 */
    @Test
    fun navigatesToEveryDestination() {
        val controller = NavigationController()

        for (destination in Destination.entries) {
            controller.navigate(destination)
            assertEquals(destination, controller.state.destination)
            assertFalse(controller.state.settingsOpen)
        }
    }

    /** 重复导航到同一目的地不改变状态。 */
    @Test
    fun repeatedNavigationKeepsState() {
        val controller = NavigationController()
        controller.navigate(Destination.MusicData)
        val first = controller.state

        controller.navigate(Destination.MusicData)

        assertEquals(first, controller.state)
    }

    /** 设置打开时导航到另一项，一次提交完成关闭设置并切换目的地。 */
    @Test
    fun navigatingWhileSettingsOpenToAnotherDestinationClosesSettings() {
        val controller = NavigationController()
        controller.openSettings()

        controller.navigate(Destination.MusicData)

        assertEquals(Destination.MusicData, controller.state.destination)
        assertFalse(controller.state.settingsOpen)
    }

    /** 设置打开时点击当前项，仍必须关闭设置。 */
    @Test
    fun navigatingWhileSettingsOpenToCurrentDestinationStillClosesSettings() {
        val controller = NavigationController()
        controller.navigate(Destination.MusicData)
        controller.openSettings()

        controller.navigate(Destination.MusicData)

        assertEquals(Destination.MusicData, controller.state.destination)
        assertFalse(controller.state.settingsOpen)
    }

    /** 打开设置保留当前目的地，重复打开无副作用。 */
    @Test
    fun openSettingsKeepsDestinationAndIsIdempotent() {
        val controller = NavigationController()
        controller.navigate(Destination.ComingSoon)

        controller.openSettings()
        val opened = controller.state
        controller.openSettings()

        assertEquals(Destination.ComingSoon, opened.destination)
        assertTrue(opened.settingsOpen)
        assertEquals(opened, controller.state)
    }

    /** 关闭设置保留当前目的地，重复关闭无副作用。 */
    @Test
    fun closeSettingsIsIdempotent() {
        val controller = NavigationController()
        controller.navigate(Destination.MusicData)
        controller.openSettings()
        controller.closeSettings()
        val closed = controller.state

        controller.closeSettings()

        assertEquals(Destination.MusicData, closed.destination)
        assertFalse(closed.settingsOpen)
        assertEquals(closed, controller.state)
    }

    /** 设置关闭时返回不消费，也不修改状态。 */
    @Test
    fun consumeBackReturnsFalseWhenSettingsClosed() {
        val controller = NavigationController()
        val before = controller.state

        val consumed = controller.consumeBack()

        assertFalse(consumed)
        assertEquals(before, controller.state)
    }

    /** 设置打开时返回只关闭设置，连续两次返回第二次不再消费。 */
    @Test
    fun consumeBackClosesSettingsOnceOnly() {
        val controller = NavigationController()
        controller.navigate(Destination.MusicData)
        controller.openSettings()

        val first = controller.consumeBack()
        val second = controller.consumeBack()

        assertTrue(first)
        assertFalse(second)
        assertEquals(Destination.MusicData, controller.state.destination)
        assertFalse(controller.state.settingsOpen)
    }

    /** canHandleBack 跟随设置开关派生，不需要额外存储。 */
    @Test
    fun canHandleBackTracksSettings() {
        val controller = NavigationController()
        assertFalse(controller.canHandleBack)

        controller.openSettings()
        assertTrue(controller.canHandleBack)

        controller.closeSettings()
        assertFalse(controller.canHandleBack)
    }

    /** 恢复出的设置已打开时，第一下返回必须立即可用并关闭设置。 */
    @Test
    fun restoredOpenSettingsCanHandleBackImmediately() {
        val restored = decodeNavigationState(
            version = NAVIGATION_STATE_VERSION,
            route = Destination.MusicData.savedValue,
            settingsOpen = true,
        )
        val controller = NavigationController(restored)

        assertTrue(controller.canHandleBack)
        assertTrue(controller.consumeBack())
        assertFalse(controller.canHandleBack)
        assertEquals(Destination.MusicData, controller.state.destination)
    }
}
