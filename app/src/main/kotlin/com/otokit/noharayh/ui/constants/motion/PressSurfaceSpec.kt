package com.otokit.noharayh.ui.constants.motion

/** 内容按压的共享参数；标题和导航保留各自独立参数。 */
internal object PressSurfaceSpec {
    const val FOLLOW_TAU_MS = 30f
    const val MAX_DIAMETER_DP = 120f
    const val REST_DIAMETER_DP = 106f

    const val WHITE_ALPHA = 0.16f
    const val BLACK_ALPHA = 56

    /** 形状每边内收量：按下时半径缩 3dp。 */
    const val INSET_DP = 3f

    const val MAX_RIPPLES = 8

    const val EXPAND_DURATION_MS = 120L

    /** 按下与点击释放两次触觉的最小间隔。 */
    const val MIN_HAPTIC_GAP_MS = 120L

    /** 需要离开当前窗口的操作，给已受理的抬起反馈留出播放时间。 */
    const val RELEASE_FEEDBACK_SETTLE_MS = 60L

    const val MIN_VISIBLE_CHANGE = 0.002f

    const val PRESS_DAMPING = 0.85f
    const val PRESS_STIFFNESS = 1000f

    const val EXPAND_DAMPING = 0.8f
    const val EXPAND_STIFFNESS = 1600f

    const val REST_DAMPING = 0.9f
    const val REST_STIFFNESS = 300f

    const val RELEASE_DAMPING = 1f
    const val RELEASE_STIFFNESS = 700f
}
