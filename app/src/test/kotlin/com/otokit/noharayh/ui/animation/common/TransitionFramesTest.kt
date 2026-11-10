package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.motion.TransitionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransitionFramesTest {

    /** 组合类型在两端分别落到起点与恒等值。 */
    @Test
    fun comboEntersFromConfiguredFrame() {
        val start = TransitionFrames.enter(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 0f,
            scaleFrom = 0.85f,
            translationYFrom = 120f,
        )
        val end = TransitionFrames.enter(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 1f,
            scaleFrom = 0.85f,
            translationYFrom = 120f,
        )

        assertEquals(0f, start.alpha, 0.0001f)
        assertEquals(0.85f, start.scale, 0.0001f)
        assertEquals(120f, start.translationY, 0.0001f)
        assertEquals(1f, end.alpha, 0.0001f)
        assertEquals(1f, end.scale, 0.0001f)
        assertEquals(0f, end.translationY, 0.0001f)
    }

    /** 类型只改变允许的属性，其他属性保持恒等。 */
    @Test
    fun kindMasksUnrelatedProperties() {
        val fade = TransitionFrames.enter(
            kind = TransitionKind.Fade,
            progress = 0.25f,
            scaleFrom = 0.5f,
            translationYFrom = 80f,
        )
        assertEquals(0.25f, fade.alpha, 0.0001f)
        assertEquals(1f, fade.scale, 0.0001f)
        assertEquals(0f, fade.translationY, 0.0001f)

        val slide = TransitionFrames.enter(
            kind = TransitionKind.Slide,
            progress = 0.25f,
            translationYFrom = 80f,
        )
        assertEquals(1f, slide.alpha, 0.0001f)
        assertEquals(60f, slide.translationY, 0.0001f)

        val scale = TransitionFrames.enter(
            kind = TransitionKind.Scale,
            progress = 0.5f,
            scaleFrom = 0.5f,
        )
        assertEquals(0.75f, scale.scale, 0.0001f)
        assertEquals(0f, scale.translationY, 0.0001f)
    }

    /** 退出方向从恒等值走向配置终点；反向由进度重算。 */
    @Test
    fun exitMovesFromIdentityToConfiguredFrame() {
        val start = TransitionFrames.exit(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 0f,
            scaleTo = 0.85f,
            translationYTo = -120f,
        )
        val mid = TransitionFrames.exit(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 0.5f,
            scaleTo = 0.85f,
            translationYTo = -120f,
        )
        val end = TransitionFrames.exit(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 1f,
            scaleTo = 0.85f,
            translationYTo = -120f,
        )

        assertEquals(1f, start.alpha, 0.0001f)
        assertEquals(0.5f, mid.alpha, 0.0001f)
        assertEquals(0.925f, mid.scale, 0.0001f)
        assertEquals(-60f, mid.translationY, 0.0001f)
        assertEquals(0f, end.alpha, 0.0001f)
        assertEquals(0.85f, end.scale, 0.0001f)
        assertEquals(-120f, end.translationY, 0.0001f)
    }

    /** Mai/Chu 端点固定，不随手势方向翻转；中间帧反向可得到对应值。 */
    @Test
    fun pagerPairHasStableEndpointsAndReversibleMiddle() {
        val (maiStart, chuStart) = TransitionFrames.pagerPair(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 0f,
            slideDistancePx = 1000f,
            minScale = 0.7f,
            minAlpha = 0f,
        )
        assertEquals(0f, maiStart.translationX, 0.0001f)
        assertEquals(1f, maiStart.scale, 0.0001f)
        assertEquals(1f, maiStart.alpha, 0.0001f)
        assertEquals(1000f, chuStart.translationX, 0.0001f)
        assertEquals(0.7f, chuStart.scale, 0.0001f)
        assertEquals(0f, chuStart.alpha, 0.0001f)

        val (maiMid, chuMid) = TransitionFrames.pagerPair(
            kind = TransitionKind.ScaleFadeSlide,
            progress = 0.25f,
            slideDistancePx = 1000f,
            minScale = 0.7f,
            minAlpha = 0f,
        )
        assertEquals(-250f, maiMid.translationX, 0.0001f)
        assertEquals(0.925f, maiMid.scale, 0.0001f)
        assertEquals(0.75f, maiMid.alpha, 0.0001f)
        assertEquals(750f, chuMid.translationX, 0.0001f)
        assertTrue(chuMid.alpha < maiMid.alpha)
    }
}
