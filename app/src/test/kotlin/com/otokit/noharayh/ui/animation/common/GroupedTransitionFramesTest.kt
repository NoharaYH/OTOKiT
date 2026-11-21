package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.animation.chrome.TitleTransitionFrames
import com.otokit.noharayh.ui.animation.shell.SettingsTransitionFrames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 直接测试呈现器实际使用的空间帧；同一测试也用于更换全局配置后的矩阵。 */
class GroupedTransitionFramesTest {
    @Test fun replacementGhostContinuesFromItsPresentedFrame() {
        val presented = TransitionFrame(0.4f, 0.9f, 0f, 12f)
        assertEquals(presented, TitleTransitionFrames.navGhostExit(0f, -40f, presented))
        assertEquals(
            TitleTransitionFrames.navGhostExit(1f, -40f),
            TitleTransitionFrames.navGhostExit(1f, -40f, presented),
        )
    }

    @Test fun springOvershootMovesPagesWithoutOvershootingOpacity() {
        val p = 1.05f
        val entry = TitleTransitionFrames.navEntry(p, 40f)
        assertTrue(entry.translationY < 0f)
        assertEquals(1f, entry.alpha, 0f)
        assertEquals(1f, entry.scale, 0f)
        assertTrue(SettingsPageFrames.unfoldPage(p, true, true, 1f).translationX < 0f)
    }

    @Test fun settingsOverlayAndTitleShareTheSameFrame() {
        for (p in listOf(-.05f, 0f, .5f, 1f, 1.05f)) {
            val feature = SettingsTransitionFrames.feature(p)
            val overlay = SettingsTransitionFrames.overlay(p, 20f)
            assertEquals(feature, TitleTransitionFrames.settingsFeature(p))
            assertEquals(overlay, TitleTransitionFrames.settingsOverlay(p, 20f))
            assertEquals(0f, feature.translationY, 0f)
            assertEquals(20f * (1f - p), overlay.translationY, .0001f)
            assertEquals(p.coerceIn(0f, 1f), overlay.alpha, 0f)
            assertEquals(1f, overlay.scale, 0f)
        }
    }

    @Test fun configuredSettingsAlwaysReturnToLegalVisibleEndpoints() {
        listOf(SettingsTransitionFrames.feature(0f), TitleTransitionFrames.settingsFeature(0f),
            SettingsTransitionFrames.overlay(1f, 1000f), TitleTransitionFrames.settingsOverlay(1f, 100f)
        ).forEach {
            assertEquals(1f, it.alpha, 0.0001f)
            assertEquals(1f, it.scale, 0.0001f)
            assertEquals(0f, it.translationY, 0.0001f)
        }
    }
}
