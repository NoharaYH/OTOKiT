package com.otokit.noharayh.ui.window

import com.otokit.noharayh.ui.window.breakpoints.WindowLayoutClass
import com.otokit.noharayh.ui.window.insets.WindowInsetsSnapshot
import com.otokit.noharayh.ui.window.metrics.WindowMetricsSnapshot

/**
 * 本轮测量、Insets 与分类组成的只读聚合快照。
 *
 * 只含纯值：保留原始分项信息，不只给消费方一个已经混合过的数字。
 */
data class WindowSnapshot(
    val metrics: WindowMetricsSnapshot,
    val insets: WindowInsetsSnapshot,
    val breakpointWidthDp: Float,
    val layoutClass: WindowLayoutClass,
) {
    val isValid: Boolean get() = metrics.isValid
}
