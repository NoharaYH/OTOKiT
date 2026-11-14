package com.otokit.noharayh.ui.constants.motion

/** 不可变时序输入；默认值由使用此过渡的组件或效果提供。 */
internal data class TransitionTiming(val durationMs: Long, val curve: MotionCurve) {
    init { require(durationMs >= 0) }
}
