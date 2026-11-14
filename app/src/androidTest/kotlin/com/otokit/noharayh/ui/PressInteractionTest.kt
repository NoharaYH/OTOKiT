package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.View
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.animation.common.PressFeedback
import com.otokit.noharayh.ui.constants.motion.PressClickSpec
import com.otokit.noharayh.ui.constants.motion.PressHapticSpec
import com.otokit.noharayh.ui.kit.common.PressInteraction
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PressInteractionTest : UiComponentTestHost() {
    @Test fun acceptedFeedbackCompletesBeforeDeferredClick() {
        val done = CountDownLatch(1)
        val events = mutableListOf<String>()
        var pressedAt = 0L; var releasedAt = 0L; var clickedAt = 0L
        lateinit var button: View
        lateinit var interaction: PressInteraction
        main {
            button = View(activity).apply { setOnClickListener {
                clickedAt = SystemClock.uptimeMillis(); events += "click"; done.countDown()
            } }
            interaction = PressInteraction(button, object : PressFeedback {
                override fun press() { pressedAt = SystemClock.uptimeMillis(); events += "press" }
                override fun release(target: View): Boolean {
                    assertSame(button, target)
                    releasedAt = SystemClock.uptimeMillis(); events += "release"; return true
                }
            })
        }
        mount(button)
        main {
            interaction.press(); interaction.click(defer = true, releaseEnabled = true)
            assertTrue(interaction.deferredClick)
            assertEquals(listOf("press"), events)
        }
        assertTrue(done.await(3, TimeUnit.SECONDS))
        main {
            assertEquals(listOf("press", "release", "click"), events)
            assertTrue(releasedAt - pressedAt >= PressHapticSpec.MIN_HAPTIC_GAP_MS)
            assertTrue(clickedAt - releasedAt >= PressClickSpec.RELEASE_FEEDBACK_SETTLE_MS)
            assertFalse(interaction.deferredClick)
        }
    }

    @Test fun rejectedFeedbackDispatchesWithoutAnExtraSettleTask() {
        val done = CountDownLatch(1)
        val events = mutableListOf<String>()
        lateinit var button: View
        lateinit var interaction: PressInteraction
        main {
            button = View(activity).apply { setOnClickListener { events += "click" } }
            interaction = PressInteraction(button, object : PressFeedback {
                override fun press() { events += "press" }
                override fun release(target: View): Boolean {
                    events += "release"
                    target.post { events += "next-loop"; done.countDown() }
                    return false
                }
            })
        }
        mount(button)
        main { interaction.press(); interaction.click(true, true) }
        assertTrue(done.await(3, TimeUnit.SECONDS))
        main { assertEquals(listOf("press", "release", "click", "next-loop"), events) }
    }

    @Test fun acceptedImmediateClickKeepsWindowFeedbackAfterSourceRemoval() {
        val released = CountDownLatch(1)
        val events = mutableListOf<String>()
        lateinit var button: View
        lateinit var interaction: PressInteraction
        lateinit var window: View
        main {
            button = View(activity).apply {
                isHapticFeedbackEnabled = true
                setOnClickListener { events += "click"; host.removeView(this); interaction.cancelDeferredClick() }
            }
            interaction = PressInteraction(button, object : PressFeedback {
                override fun press() { events += "press" }
                override fun release(target: View): Boolean {
                    assertSame(window, target); events += "release"; released.countDown(); return true
                }
            })
        }
        mount(button)
        main {
            window = button.rootView
            interaction.press(); interaction.click(false, true)
            assertEquals(listOf("press", "click"), events)
            assertFalse(button.isAttachedToWindow)
        }
        assertTrue(released.await(3, TimeUnit.SECONDS))
        main { assertEquals(listOf("press", "click", "release"), events) }
    }

    @Test fun cancellationDropsDeferredFeedbackAndClick() {
        val unexpected = CountDownLatch(1)
        lateinit var button: View
        lateinit var interaction: PressInteraction
        main {
            button = View(activity).apply { setOnClickListener { unexpected.countDown() } }
            interaction = PressInteraction(button, object : PressFeedback {
                override fun press() = Unit
                override fun release(target: View): Boolean { unexpected.countDown(); return true }
            })
        }
        mount(button)
        main {
            interaction.press(); interaction.click(true, true)
            interaction.cancelDeferredClick(); host.removeView(button)
        }
        assertFalse(unexpected.await(350, TimeUnit.MILLISECONDS))
        main { assertFalse(interaction.deferredClick) }
    }
}
