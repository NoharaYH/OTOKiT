package com.otokit.noharayh.ui.constants.motion

/** 内容状态过渡默认值；不依赖主题或颜色编辑业务。 */
internal object ContainerMotion {
    const val DURATION_MS = 300L
    const val CONTENT_SHIFT_DP = 12f
    val CURVE = MotionCurve.FastOutSlowIn
}
