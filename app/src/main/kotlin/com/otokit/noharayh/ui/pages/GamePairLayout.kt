package com.otokit.noharayh.ui.pages

import android.view.View
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.animation.common.TransitionFrame
import com.otokit.noharayh.ui.animation.common.TransitionFrames
import com.otokit.noharayh.ui.constants.motion.PagerPairTransition

/** 两个音游页面跟随主壳的同一帧，不产生新的选择或动画时钟。 */
internal class GamePairLayout(
    private val mai: View,
    private val chu: View,
    private val onInteraction: (Boolean, Boolean) -> Unit,
) : FrameLayout(mai.context) {
    private var frame: GameFrame? = null
    private var interactive = true

    init {
        addView(mai)
        addView(chu)
        addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> frame?.let(::render) }
    }

    fun setInteractive(allowed: Boolean) {
        interactive = allowed
        frame?.let(::render)
    }

    fun render(value: GameFrame) {
        frame = value
        val idle = value.phase == GamePhase.Idle
        onInteraction(idle && interactive && value.selectedMai, idle && interactive && !value.selectedMai)
        if (idle) {
            endpoint(mai, value.selectedMai)
            endpoint(chu, !value.selectedMai)
            return
        }
        val (left, right) = TransitionFrames.pagerPair(
            PagerPairTransition.KIND, value.normalizedProgress,
            width * PagerPairTransition.CONTENT_TRANSLATION_RATIO,
            PagerPairTransition.MIN_SCALE, PagerPairTransition.MIN_ALPHA, cardMotion = true,
        )
        apply(mai, left, 0f, value.edgePullPx)
        apply(chu, right, chu.width.toFloat(), value.edgePullPx)
    }

    private fun endpoint(panel: View, visible: Boolean) {
        panel.visibility = if (visible) VISIBLE else GONE
        panel.alpha = 1f
        panel.scaleX = 1f
        panel.scaleY = 1f
        panel.translationX = 0f
        panel.translationY = 0f
    }

    private fun apply(panel: View, pose: TransitionFrame, pivot: Float, edgePull: Float) {
        panel.visibility = VISIBLE
        panel.alpha = pose.alpha
        panel.scaleX = pose.scale
        panel.scaleY = pose.scale
        panel.translationX = TransitionFrames.rebasePivotTranslation(pose.translationX, pose.scale, pivot, panel.pivotX) + edgePull
        panel.translationY = pose.translationY
    }
}
