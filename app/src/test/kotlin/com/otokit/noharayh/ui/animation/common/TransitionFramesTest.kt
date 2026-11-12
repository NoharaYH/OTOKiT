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
        assertEquals(0.969374f, maiMid.scale, 0.0001f)
        assertEquals(0.75f, maiMid.alpha, 0.0001f)
        assertEquals(750f, chuMid.translationX, 0.0001f)
        assertTrue(chuMid.alpha < maiMid.alpha)
    }

    @Test
    fun pageShrinkHasNoFlatIntervalsAndGentlerMiddle() {
        for (minimum in listOf(0.1f, 0.4f, 0.7f, 0.85f)) {
            val frames = (0..4).map { TransitionFrames.pageScale(1f, minimum, it / 4f, entering = false) }
            assertEquals(1f, frames.first(), 0f)
            assertEquals(minimum, frames.last(), 0f)
            assertTrue(frames.zipWithNext().all { (a, b) -> a > b })
            // 终点附近仍连续缩小，不能只在 p=1 时突然跳到最小尺寸。
            val nearEnd = TransitionFrames.pageScale(1f, minimum, 0.999999f, entering = false)
            assertEquals(minimum, nearEnd, 0.0001f)
            var previous = 1f
            for (i in 0..1000) {
                val p = i / 1000f
                val outgoing = TransitionFrames.pageScale(1f, minimum, p, entering = false)
                val incoming = TransitionFrames.pageScale(minimum, 1f, 1f - p, entering = true)
                assertEquals(outgoing, incoming, 0.0001f)
                assertTrue(outgoing.isFinite() && outgoing in minimum..1f)
                if (i > 0) {
                    assertTrue(outgoing < previous)
                    assertTrue(previous - outgoing < 0.002f)
                }
                previous = outgoing
            }
        }
        // 接管尚未完成的转场时，从当前缩放值连续开始。
        assertEquals(0.94f, TransitionFrames.pageScale(0.94f, 0.85f, 0f, false), 0f)
        assertEquals(0.775293f, TransitionFrames.pageScale(1f, 0.1f, 0.4f, false), 0.0001f)
        assertEquals(0.497491f, TransitionFrames.pageScale(1f, 0.1f, 0.6f, false), 0.0001f)
    }

    @Test
    fun gameScaleIsSymmetricWhileAlphaAndTranslationRemainLinear() {
        for (i in 0..100) {
            val p = i / 100f
            val (mai, chu) = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, p, 1000f, 0.7f, 0f)
            val (reverseMai, reverseChu) = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, 1f - p, 1000f, 0.7f, 0f)
            assertEquals(mai.scale, reverseChu.scale, 0.0001f)
            assertEquals(chu.scale, reverseMai.scale, 0.0001f)
            assertEquals(1f - p, mai.alpha, 0.0001f)
            assertEquals(p, chu.alpha, 0.0001f)
            assertEquals(-1000f * p, mai.translationX, 0.0001f)
            assertEquals(1000f * (1f - p), chu.translationX, 0.0001f)
        }
    }

    @Test
    fun cardsRemainVisibleDuringTheDropAndRetraceAllProperties() {
        val atForty = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, 0.4f, 420f, 0.1f, 0f, cardMotion = true).first
        val atSixty = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, 0.6f, 420f, 0.1f, 0f, cardMotion = true).first
        assertTrue(atForty.alpha > 0.9f)
        assertTrue(atSixty.scale in 0.45f..0.55f && atSixty.alpha > 0.6f)
        val slowTravel = TransitionFrames.pageTravelProgress(0.3f, false) - TransitionFrames.pageTravelProgress(0.2f, false)
        val fastTravel = TransitionFrames.pageTravelProgress(0.6f, false) - TransitionFrames.pageTravelProgress(0.5f, false)
        assertTrue(fastTravel > slowTravel * 2f && fastTravel < slowTravel * 3f)
        var previousScale = 1f
        var previousTravel = 0f
        var previousAlpha = 1f
        for (i in 0..1000) {
            val p = i / 1000f
            val exit = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, p, 420f, 0.1f, 0f, cardMotion = true).first
            val reverse = TransitionFrames.pagerPair(TransitionKind.ScaleFadeSlide, 1f - p, 420f, 0.1f, 0f, cardMotion = true).second
            assertEquals(exit.scale, reverse.scale, 0.0001f)
            assertEquals(exit.alpha, reverse.alpha, 0.0001f)
            assertEquals(-exit.translationX, reverse.translationX, 0.0001f)
            assertEquals((1f - exit.scale) / 0.9f, -exit.translationX / 420f, 0.0001f)
            assertTrue(exit.scale <= previousScale && exit.alpha <= previousAlpha && -exit.translationX >= previousTravel)
            previousScale = exit.scale
            previousAlpha = exit.alpha
            previousTravel = -exit.translationX
        }
    }

    @Test
    fun anchoredCardEdgesKeepMovingOutwardWhileShrinking() {
        // 横向 90% 宽、纵向 70% 高的卡片，以及满幅内容均检查两侧退出。
        for (extent in listOf(720f, 1272f, 2772f)) for (inset in listOf(0f, 0.05f, 0.15f)) {
            for (direction in listOf(-1f, 1f)) {
                val anchor = if (direction < 0f) 0f else extent
                var previousLeading = if (direction < 0f) extent * inset else extent * (1f - inset)
                var previousTrailing = if (direction < 0f) extent * (1f - inset) else extent * inset
                for (i in 0..1000) {
                    val p = i / 1000f
                    val scale = TransitionFrames.pageScale(1f, 0.1f, p, false)
                    val travel = direction * extent * 0.24f * TransitionFrames.pageTravelProgress(p, false)
                    val translation = TransitionFrames.rebasePivotTranslation(travel, scale, anchor, extent / 2f)
                    fun screenPoint(local: Float) = translation + (1f - scale) * extent / 2f + scale * local
                    val leading = screenPoint(if (direction < 0f) extent * inset else extent * (1f - inset))
                    val trailing = screenPoint(if (direction < 0f) extent * (1f - inset) else extent * inset)
                    assertTrue(direction * (leading - previousLeading) >= -0.001f)
                    assertTrue(direction * (trailing - previousTrailing) >= -0.001f)
                    previousLeading = leading
                    previousTrailing = trailing
                }
            }
        }
    }

    @Test
    fun changingScaleAnchorPreservesEveryVisiblePointOnInterruption() {
        for (scale in listOf(0.1f, 0.4f, 0.8f, 1f)) for (newPivot in listOf(0f, 1500f)) {
            val oldPivot = 750f
            val originalTranslation = -123.5f
            val rebased = TransitionFrames.rebasePivotTranslation(originalTranslation, scale, oldPivot, newPivot)
            for (point in listOf(0f, 100f, 750f, 1500f)) {
                val before = originalTranslation + (1f - scale) * oldPivot + scale * point
                val after = rebased + (1f - scale) * newPivot + scale * point
                assertEquals(before, after, 0.0002f)
            }
        }
    }
}
