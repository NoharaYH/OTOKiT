package com.otokit.noharayh.platform

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.window.layout.WindowMetricsCalculator
import com.otokit.noharayh.ui.window.orientation.WindowOrientationPolicy
import kotlin.math.min

/**
 * 平台方向执行器。
 *
 * 只在期望值与当前请求不同时写入，避免重复请求造成重建循环；
 * 最大窗口度量只用于判断平台方向能力，不回流到窗口快照或内容几何。
 */
class WindowOrientationController(private val activity: Activity) {

    fun applyForCurrentWindow() {
        val bounds = WindowMetricsCalculator.getOrCreate()
            .computeMaximumWindowMetrics(activity)
            .bounds

        val lockPortrait = WindowOrientationPolicy.shouldLockPortrait(
            inMultiWindow = activity.isInMultiWindowMode,
            maxWindowShortSidePx = min(bounds.width(), bounds.height()),
            density = activity.resources.displayMetrics.density,
        )

        val desired = if (lockPortrait) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        if (activity.requestedOrientation != desired) {
            activity.requestedOrientation = desired
        }
    }
}
