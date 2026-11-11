package com.otokit.noharayh.ui.constants.motion

/**
 * 内容区按钮外溢层的公共参数。
 *
 * 黑边尺寸与抽屉胶囊/汉堡键同一视觉量级；内容区按钮只消费这一份。
 */
internal object PressHaloSpec {
    const val ALPHA = 80
    const val REST_OUTSET_DP = 6f
    const val PEAK_OUTSET_DP = 9f
    const val EXPAND_DURATION_MS = 120L
    const val MIN_VISIBLE_CHANGE = 0.002f
    const val EXPAND_DAMPING = 0.8f
    const val EXPAND_STIFFNESS = 1600f
    const val REST_DAMPING = 0.9f
    const val REST_STIFFNESS = 300f
    const val RELEASE_DAMPING = 1f
    const val RELEASE_STIFFNESS = 700f
}
