package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.animation.chrome.TitleTransitionFrames
import com.otokit.noharayh.ui.animation.shell.SettingsTransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavigationTransition
import com.otokit.noharayh.ui.constants.motion.SettingsTransition
import com.otokit.noharayh.ui.constants.motion.TransitionKind
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

    @Test fun navigationTitleUsesGroupKindAndHasRealScaleOrSlide() {
        val entry = TitleTransitionFrames.navEntry(0.5f, 40f)
        val exit = TitleTransitionFrames.navGhostExit(0.5f, -40f)
        assertEquals(NavigationTransition.KIND, TitleTransitionFrames.kind())
        when (NavigationTransition.KIND) {
            TransitionKind.Scale -> {
                assertTrue(entry.scale < 1f && entry.scale > 0f)
                assertTrue(exit.scale < 1f && exit.scale > 0f)
                assertEquals(1f, entry.alpha, 0.0001f)
                assertEquals(0f, entry.translationY, 0.0001f)
            }
            TransitionKind.Slide -> {
                assertTrue(entry.translationY > 0f && exit.translationY < 0f)
                assertEquals(1f, entry.scale, 0.0001f)
                assertEquals(1f, entry.alpha, 0.0001f)
            }
            TransitionKind.Fade -> {
                assertEquals(0.5f, entry.alpha, 0.0001f)
                assertEquals(0f, entry.translationY, 0.0001f)
                assertEquals(1f, entry.scale, 0.0001f)
            }
            TransitionKind.ScaleFadeSlide -> {
                assertEquals(0.5f, entry.alpha, 0.0001f)
                assertTrue(entry.translationY > 0f && exit.translationY < 0f)
            }
        }
    }

    @Test fun settingContentAndTitleSupportEveryConfiguredKind() {
        val content = SettingsTransitionFrames.feature(0.5f, 1000f)
        val overlay = SettingsTransitionFrames.overlay(0.5f, 1000f)
        val title = TitleTransitionFrames.settingsFeature(0.5f, 100f)
        val settingTitle = TitleTransitionFrames.settingsOverlay(0.5f, 100f)
        val frames = listOf(content, overlay, title, settingTitle)
        when (SettingsTransition.KIND) {
            TransitionKind.Slide -> {
                assertTrue(content.translationY < 0f && title.translationY < 0f)
                assertTrue(overlay.translationY > 0f && settingTitle.translationY > 0f)
                frames.forEach {
                    assertEquals(1f, it.scale, 0.0001f)
                    assertEquals(1f, it.alpha, 0.0001f)
                }
            }
            TransitionKind.Scale -> {
                assertTrue(content.scale < 1f && title.scale < 1f)
                assertTrue(overlay.scale > 1f && settingTitle.scale > 1f)
                frames.forEach {
                    assertEquals(1f, it.alpha, 0.0001f)
                    assertEquals(0f, it.translationY, 0.0001f)
                }
            }
            TransitionKind.Fade -> frames.forEach {
                assertEquals(0.5f, it.alpha, 0.0001f)
                assertEquals(1f, it.scale, 0.0001f)
                assertEquals(0f, it.translationY, 0.0001f)
            }
            TransitionKind.ScaleFadeSlide -> {
                frames.forEach { assertEquals(0.5f, it.alpha, 0.0001f) }
                assertTrue(content.scale < 1f && title.scale < 1f)
                assertTrue(overlay.scale > 1f && settingTitle.scale > 1f)
            }
        }
    }

    @Test fun configuredSettingsAlwaysReturnToLegalVisibleEndpoints() {
        listOf(SettingsTransitionFrames.feature(0f, 1000f), TitleTransitionFrames.settingsFeature(0f, 100f),
            SettingsTransitionFrames.overlay(1f, 1000f), TitleTransitionFrames.settingsOverlay(1f, 100f)
        ).forEach {
            assertEquals(1f, it.alpha, 0.0001f)
            assertEquals(1f, it.scale, 0.0001f)
            assertEquals(0f, it.translationY, 0.0001f)
        }
    }
}
