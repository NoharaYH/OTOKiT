package com.otokit.noharayh.ui.window.orientation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowOrientationPolicyTest {

    /** 普通手机全屏（短边小于 600dp）请求竖屏。 */
    @Test
    fun locksPortraitForPhoneSizedWindow() {
        assertTrue(WindowOrientationPolicy.shouldLockPortrait(false, 1200, 2.8f))
        assertTrue(WindowOrientationPolicy.shouldLockPortrait(false, 1679, 2.8f))
    }

    /** 短边达到 600dp 即释放给系统，不再锁竖屏。 */
    @Test
    fun releasesAtCompactBoundary() {
        assertTrue(WindowOrientationPolicy.shouldLockPortrait(false, 1679, 2.8f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 1680, 2.8f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 2400, 2.8f))
    }

    /** 多窗口 / 自由窗口一律不锁方向，即使当前窗口很窄。 */
    @Test
    fun releasesInMultiWindowMode() {
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(true, 800, 2.8f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(true, 2400, 2.8f))
    }

    /** 无效度量不强制锁定。 */
    @Test
    fun ignoresInvalidMetrics() {
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 0, 2.8f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, -100, 2.8f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 1200, 0f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 1200, -1f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 1200, Float.NaN))
    }

    /** 相同 dp 在不同 density 下得到相同结论。 */
    @Test
    fun comparesInDpNotPx() {
        assertTrue(WindowOrientationPolicy.shouldLockPortrait(false, 1198, 2f))
        assertFalse(WindowOrientationPolicy.shouldLockPortrait(false, 2400, 2f))
    }
}
