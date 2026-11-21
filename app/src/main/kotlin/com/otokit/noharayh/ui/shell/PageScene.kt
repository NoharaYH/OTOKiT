package com.otokit.noharayh.ui.shell

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.common.MotionInterpolators
import com.otokit.noharayh.ui.animation.common.PageMotionHost
import com.otokit.noharayh.ui.constants.motion.MotionCurve

/** 页面滚动从内容宿主读取当前动效，导航样式由外层选择。 */
class PageScene(context: Context, attrs: AttributeSet?) : FrameLayout(context, attrs), PageMotionHost {
    override var settleInterpolator = MotionInterpolators.of(MotionCurve.Standard)
        internal set
}
