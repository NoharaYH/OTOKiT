package com.otokit.noharayh.ui.animation.common

import android.view.animation.Interpolator
import android.view.animation.PathInterpolator
import com.otokit.noharayh.ui.constants.motion.MotionCurve

/** 纯曲线到平台 Interpolator 的唯一映射；强阻尼、无过冲。 */
object MotionInterpolators {

    fun of(curve: MotionCurve): Interpolator = when (curve) {
        MotionCurve.OutQuint -> PathInterpolator(0.22f, 1f, 0.36f, 1f)
        MotionCurve.FastOutSlowIn -> PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}