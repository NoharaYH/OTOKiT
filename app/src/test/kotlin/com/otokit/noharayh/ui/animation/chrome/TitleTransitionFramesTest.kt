package com.otokit.noharayh.ui.animation.chrome

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.Assert.assertTrue
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.NavContentTransition
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition

class TitleTransitionFramesTest {

    /** LM 锚点只取墨迹纵向中心，左缘固定为 0。 */
    @Test
    fun lmPivotUsesInkCenter() {
        assertEquals(40f, TitleTransitionFrames.lmPivotY(10f, 70f), 0.0001f)
        assertEquals(0f, TitleTransitionFrames.lmPivotY(0f, 0f), 0.0001f)
    }

    /** 默认标题导航同时位移与淡变：ghost 从可见开始，新字从透明开始。 */
    @Test
    fun navEntryAndGhostFadeAtStart() {
        val entry = TitleTransitionFrames.navEntry(0f, 30f)
        val ghost = TitleTransitionFrames.navGhostExit(0f, -30f)

        assertEquals(30f, entry.translationY, 0.0001f)
        assertEquals(0f, entry.alpha, 0.0001f)
        assertEquals(NavContentTransition.SCALE, entry.scale, 0f)
        assertEquals(0f, ghost.translationY, 0.0001f)
        assertEquals(1f, ghost.alpha, 0.0001f)
    }

    /** 到端点时新字完全可见，ghost 完全透明。 */
    @Test
    fun navEndsAtStableFrames() {
        val entry = TitleTransitionFrames.navEntry(1f, 30f)
        val ghost = TitleTransitionFrames.navGhostExit(1f, -30f)

        assertEquals(0f, entry.translationY, 0.0001f)
        assertEquals(1f, entry.alpha, 0.0001f)
        assertEquals(-30f, ghost.translationY, 0.0001f)
        assertEquals(0f, ghost.alpha, 0.0001f)
        assertEquals(NavContentTransition.SCALE, ghost.scale, 0.0001f)
        assertEquals(1f, entry.scale, 0f)
    }

    @Test
    fun navigationTitleTracksCardTimingAcrossBothDirections() {
        for (distance in listOf(-44f, 44f)) {
            var previousDistance = 0f
            for (i in 0..1000) {
                val p = i / 1000f
                val exit = TitleTransitionFrames.navGhostExit(p, distance)
                val reverseEntry = TitleTransitionFrames.navEntry(1f - p, -distance)
                assertEquals(distance * TransitionFrames.pageTravelProgress(p, false), exit.translationY, 0.0001f)
                assertEquals(1f - TransitionFrames.pageFadeProgress(p, false), exit.alpha, 0.0001f)
                assertEquals(exit.translationY, -reverseEntry.translationY, 0.0001f)
                assertEquals(exit.alpha, reverseEntry.alpha, 0.0001f)
                assertEquals(TransitionFrames.pageScale(1f, NavContentTransition.SCALE, p, false), exit.scale, 0.0001f)
                assertEquals(exit.scale, reverseEntry.scale, 0.0001f)
                if (i in 1..999) assertTrue(exit.scale < 1f && exit.scale > NavContentTransition.SCALE)
                if (i > 0) assertTrue(kotlin.math.abs(exit.translationY) > previousDistance)
                previousDistance = kotlin.math.abs(exit.translationY)
            }
        }
    }

    @Test
    fun interruptedTitleStartsAtTheCurrentlyVisibleFrame() {
        val current = TransitionFrame(0.63f, 0.72f, 0f, 17f)
        assertEquals(current, TitleTransitionFrames.navGhostExit(0f, -44f, current))
        val end = TitleTransitionFrames.navGhostExit(1f, -44f, current)
        assertEquals(-44f, end.translationY, 0f)
        assertEquals(0f, end.alpha, 0f)
    }

    @Test
    fun gameTitleMatchesPageScaleFadeAndNormalizedTravelInBothDirections() {
        val titleWidth = 360f
        val pageWidth = 720f
        for (i in 0..1000) {
            val p = i / 1000f
            val title = TitleTransitionFrames.gamePair(p, titleWidth)
            val page = TransitionFrames.pagerPair(
                kind = PagerPairTransition.KIND,
                progress = p,
                slideDistancePx = pageWidth * PagerPairTransition.CONTENT_TRANSLATION_RATIO,
                minScale = PagerPairTransition.MIN_SCALE,
                minAlpha = PagerPairTransition.MIN_ALPHA,
                cardMotion = true,
            )
            for ((titleFrame, pageFrame) in listOf(title.first to page.first, title.second to page.second)) {
                assertEquals(pageFrame.scale, titleFrame.scale, 0f)
                assertEquals(pageFrame.alpha, titleFrame.alpha, 0f)
                assertEquals(pageFrame.translationX / pageWidth, titleFrame.translationX / titleWidth, 0.0001f)
            }
        }
    }

    @Test
    fun interruptedNavigationRebasesCroppedGhostWithoutMovingInk() {
        val inkTop = 18.5f
        val inkBottom = 68.5f
        val pivot = TitleTransitionFrames.lmPivotY(inkTop, inkBottom)
        val cropTop = kotlin.math.floor(inkTop)
        val scale = 0.72f
        val currentY = 17f
        for (anchor in listOf(inkTop, inkBottom)) {
            val logicalY = TransitionFrames.rebasePivotTranslation(currentY, scale, pivot, anchor)
            val initial = TitleTransitionFrames.navGhostExit(0f, -44f, TransitionFrame(0.63f, scale, 0f, logicalY))
            val ghostY = TransitionFrames.rebasePivotTranslation(initial.translationY, initial.scale, anchor - cropTop, pivot - cropTop)
            for (ink in listOf(inkTop, inkBottom)) {
                val before = pivot + (ink - pivot) * scale + currentY
                val after = cropTop + (pivot - cropTop) + (ink - pivot) * initial.scale + ghostY
                assertEquals(before, after, 0.0001f)
            }
        }
    }
}
