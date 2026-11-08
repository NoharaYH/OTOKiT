package com.otokit.noharayh.ui.shell.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedNavigationStateTest {

    /** 所有目的地与设置开关的组合都能完整往返。 */
    @Test
    fun everyDestinationAndFlagRoundTrips() {
        for (destination in Destination.entries) {
            for (settingsOpen in listOf(false, true)) {
                val original = NavigationState(destination = destination, settingsOpen = settingsOpen)
                val saved = encodeNavigationState(original)

                val restored = decodeNavigationState(
                    version = saved.version,
                    route = saved.route,
                    settingsOpen = saved.settingsOpen,
                )

                assertEquals(original, restored)
            }
        }
    }

    /** 保存值固定为稳定字符串，且版本为 1。 */
    @Test
    fun encodedFieldsUseStableContract() {
        val saved = encodeNavigationState(NavigationState(Destination.ComingSoon, settingsOpen = true))

        assertEquals(1, saved.version)
        assertEquals("coming_soon", saved.route)
        assertTrue(saved.settingsOpen)
        assertEquals(listOf("score_sync", "music_data", "coming_soon"), Destination.entries.map { it.savedValue })
    }

    /** 版本缺失时整份回退到默认状态。 */
    @Test
    fun missingVersionFallsBackToDefault() {
        val restored = decodeNavigationState(null, Destination.MusicData.savedValue, true)

        assertEquals(NavigationState(), restored)
    }

    /** 版本未知时即使路由与开关合法也整份回退。 */
    @Test
    fun unknownVersionFallsBackToDefault() {
        val restored = decodeNavigationState(
            version = 99,
            route = Destination.MusicData.savedValue,
            settingsOpen = true,
        )

        assertEquals(NavigationState(), restored)
    }

    /** 路由缺失时整份回退到默认状态。 */
    @Test
    fun missingRouteFallsBackToDefault() {
        val restored = decodeNavigationState(NAVIGATION_STATE_VERSION, null, true)

        assertEquals(NavigationState(), restored)
    }

    /** 路由未知时整份回退到默认状态。 */
    @Test
    fun unknownRouteFallsBackToDefault() {
        val restored = decodeNavigationState(NAVIGATION_STATE_VERSION, "unknown", true)

        assertEquals(NavigationState(), restored)
    }

    /** 版本与路由合法而开关缺失时，保留目的地并按关闭处理。 */
    @Test
    fun missingSettingsOpenDefaultsToFalse() {
        val restored = decodeNavigationState(NAVIGATION_STATE_VERSION, Destination.MusicData.savedValue, null)

        assertEquals(Destination.MusicData, restored.destination)
        assertFalse(restored.settingsOpen)
    }

    /** 保存值查询不接受 null 与未知字符串。 */
    @Test
    fun destinationFromSavedValueRejectsUnknownValues() {
        assertNull(Destination.fromSavedValue(null))
        assertNull(Destination.fromSavedValue(""))
        assertNull(Destination.fromSavedValue("ScoreSync"))
    }
}
