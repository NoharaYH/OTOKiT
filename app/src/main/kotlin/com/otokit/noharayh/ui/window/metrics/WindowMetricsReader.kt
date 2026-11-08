package com.otokit.noharayh.ui.window.metrics

import android.app.Activity
import android.view.View
import androidx.window.layout.WindowMetricsCalculator

/**
 * 窗口事实的 Android 测量适配。
 *
 * 只读取窗口边界、density 与根相对位置；不计算系统安全区、不修改 View。
 * 使用当前正在显示的 Activity，避免分屏/浮窗下把整块显示器当作窗口。
 */
object WindowMetricsReader {

    fun read(activity: Activity, root: View): WindowMetricsSnapshot {
        val bounds = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
            .bounds
        val density = activity.resources.displayMetrics.density
        val location = IntArray(2)
        root.getLocationInWindow(location)

        return WindowMetricsSnapshot(
            widthPx = bounds.width(),
            heightPx = bounds.height(),
            density = density,
            rootLeftPx = location[0],
            rootTopPx = location[1],
            rootWidthPx = root.width,
            rootHeightPx = root.height,
        )
    }
}
