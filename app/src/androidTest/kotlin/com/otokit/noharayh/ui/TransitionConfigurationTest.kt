package com.otokit.noharayh.ui

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.animation.common.GroupShape
import com.otokit.noharayh.ui.constants.motion.MotionCurve
import com.otokit.noharayh.ui.constants.motion.TransitionTiming
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransitionConfigurationTest : UiComponentTestHost() {
    @Test fun genericTransitionsUseCallerTimingAndPixelRadii() {
        main { assumeTrue(ValueAnimator.areAnimatorsEnabled()) }
        lateinit var surface: View
        lateinit var transition: ContentTransition<Float>
        lateinit var shape: GroupShape
        var top = 0f; var bottom = 0f
        main { surface = View(activity) }
        mount(surface)
        main {
            shape = GroupShape(surface, 7f, 23f, TransitionTiming(0L, MotionCurve.OutQuint)) { a, b ->
                top = a; bottom = b
            }
            shape.setEdges(0.25f, 0.75f)
            transition = ContentTransition(10f, TransitionTiming(10_000L, MotionCurve.OutQuint),
                { a, b, t -> a + (b - a) * t }, {})
            transition.play(20f)
        }
        SystemClock.sleep(500) // 超过旧固定 300ms，注入的长过渡仍不应结束。
        main {
            try {
                assertEquals(11f, top, 0.001f)
                assertEquals(19f, bottom, 0.001f)
                assertTrue(transition.isRunning)
                assertTrue(transition.frame < 20f)
            } finally { transition.finish(); shape.finish() }
            assertEquals(20f, transition.frame, 0f)
            assertFalse(transition.isRunning)
        }
    }

    @Test fun externalIconAndDeferredClickAreIndependentInputs() {
        main {
            val externalImmediate = SettingsPageViews.action(activity, "External", SettingsButton.ActionIcon.ExternalLink) {}
            val nextDeferred = SettingsPageViews.action(activity, "Next", deferClick = true) {}
            assertFalse(externalImmediate.deferClickUntilReleaseFeedback)
            assertTrue(nextDeferred.deferClickUntilReleaseFeedback)
        }
    }
}
