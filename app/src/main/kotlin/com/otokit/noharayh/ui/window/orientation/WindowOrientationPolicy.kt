package com.otokit.noharayh.ui.window.orientation

import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy

/**
 * 平台方向能力的纯决策。
 *
 * 只回答“是否需要为普通手机全屏请求竖屏”，不读取 View、不执行系统请求：
 * - 多窗口 / 自由窗口一律释放给系统；
 * - 度量无效时不强制锁定；
 * - 其余按显示可达最大窗口的短边与 Compact 边界比较。
 */
object WindowOrientationPolicy {

    fun shouldLockPortrait(
        inMultiWindow: Boolean,
        maxWindowShortSidePx: Int,
        density: Float,
        compactMaxWidthDp: Float = WindowBreakpointPolicy.COMPACT_MAX_WIDTH_DP,
    ): Boolean {
        if (inMultiWindow) return false
        if (maxWindowShortSidePx <= 0) return false
        if (!density.isFinite() || density <= 0f) return false
        return maxWindowShortSidePx / density < compactMaxWidthDp
    }
}
