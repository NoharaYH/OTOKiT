package com.otokit.noharayh.ui.animation.shell

import com.otokit.noharayh.ui.animation.common.TransitionFrame

/** Shared by the live overlay, predictive back and the exit snapshot. */
object SettingsTransitionFrames {
    fun feature(progress: Float) = TransitionFrame((1f - progress).coerceIn(0f, 1f), 1f, 0f, 0f)

    fun overlay(progress: Float, distance: Float) =
        TransitionFrame(progress.coerceIn(0f, 1f), 1f, 0f, distance * (1f - progress))
}
