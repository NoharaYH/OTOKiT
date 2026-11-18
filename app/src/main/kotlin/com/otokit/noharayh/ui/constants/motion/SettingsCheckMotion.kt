package com.otokit.noharayh.ui.constants.motion

internal object SettingsCheckMotion {
    const val ROTATION_DEGREES = 540f
    const val SWAP_SCALE = 0.72f
    val ROTATION = TransitionTiming(720L, MotionCurve.OutQuint)
    val SWAP = TransitionTiming(360L, MotionCurve.FastOutSlowIn)
}
