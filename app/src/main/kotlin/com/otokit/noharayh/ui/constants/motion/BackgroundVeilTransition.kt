package com.otokit.noharayh.ui.constants.motion

/** 背景 HTML 首帧遮罩：灰色铺底，首帧就绪或超时后淡出。 */
object BackgroundVeilTransition {
    const val COLOR_ARGB: Int = 0xFFE0E0E0.toInt()
    const val FADE_DURATION_MS: Long = 400L
    val CURVE: MotionCurve = MotionCurve.OutQuint

    /** 本机冷启动实测视觉首帧约 228–340ms；取 400ms 作为上限。 */
    const val TIMEOUT_MS: Long = 500L
}
