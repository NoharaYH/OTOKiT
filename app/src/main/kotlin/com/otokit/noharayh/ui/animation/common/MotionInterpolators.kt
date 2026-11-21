package com.otokit.noharayh.ui.animation.common

import android.graphics.Path
import android.view.animation.Interpolator
import android.view.View
import android.view.animation.PathInterpolator
import com.otokit.noharayh.ui.constants.motion.MotionCurve

/** Read the hosting shell's current selection when a gesture is released. */
internal interface PageMotionHost {
    val settleInterpolator: Interpolator
}

internal fun View.pageSettleInterpolator(): Interpolator {
    var ancestor = parent
    while (ancestor != null) {
        if (ancestor is PageMotionHost) return ancestor.settleInterpolator
        ancestor = ancestor.parent
    }
    return MotionInterpolators.of(MotionCurve.Standard)
}

/** 纯曲线到平台 Interpolator 的唯一映射。 */
object MotionInterpolators {

    fun of(curve: MotionCurve): Interpolator = when (curve) {
        MotionCurve.Standard -> PathInterpolator(.2f, 0f, 0f, 1f)
        MotionCurve.Emphasized -> PathInterpolator(Path().apply {
            moveTo(0f, 0f)
            cubicTo(.05f, 0f, .133333f, .06f, .166666f, .4f)
            cubicTo(.208333f, .82f, .25f, 1f, 1f, 1f)
        })
        MotionCurve.GentleBounce -> Interpolator { t ->
            if (t >= 1f) 1f else 1f - kotlin.math.exp(-6f * t) *
                (kotlin.math.cos(7f * t) + 6f / 7f * kotlin.math.sin(7f * t))
        }
        MotionCurve.OutQuint -> PathInterpolator(0.22f, 1f, 0.36f, 1f)
        MotionCurve.FastOutSlowIn -> PathInterpolator(0.4f, 0f, 0.2f, 1f)
    }
}
