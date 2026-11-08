package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.breakpoints.WindowBreakpointPolicy
import com.otokit.noharayh.ui.window.insets.EdgeInsets
import com.otokit.noharayh.ui.window.insets.WindowInsetsSnapshot
import com.otokit.noharayh.ui.window.metrics.WindowMetricsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class ContentGeometryTest {

    /** 系统顶区 24px 与应用标题行 72px 得到内容顶边 96px，而不是 120px。 */
    @Test
    fun addsSystemTopAndChromeTopOnce() {
        val snapshot = snapshot(
            height = 1200,
            insets = WindowInsetsSnapshot(statusBars = EdgeInsets(top = 24)),
        )

        val rects = ContentGeometry.compute(snapshot, AppChromeInsets(top = 72))

        assertEquals(24 + 72, rects.base.top)
        assertEquals(24 + 72, rects.interactive.top)
    }

    /** 状态栏与开孔同边重叠时取最大占用，不是相加。 */
    @Test
    fun mergesOverlappingTopInsets() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(
                statusBars = EdgeInsets(top = 24),
                displayCutout = EdgeInsets(top = 32),
            ),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(32, rects.base.top)
    }

    /** 窗口标题栏与状态栏同边重叠同样只取最大占用。 */
    @Test
    fun mergesCaptionBarWithStatusBar() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(
                statusBars = EdgeInsets(top = 24),
                captionBar = EdgeInsets(top = 48),
            ),
        )

        assertEquals(48, ContentGeometry.compute(snapshot).base.top)
    }

    /** 底栏 24px、IME 300px 时基础底边 976px、交互底边 700px，不能算成 676px。 */
    @Test
    fun shrinksInteractiveRectByImeOnly() {
        val snapshot = snapshot(
            height = 1000,
            insets = WindowInsetsSnapshot(
                navigationBars = EdgeInsets(bottom = 24),
                ime = EdgeInsets(bottom = 300),
                imeVisible = true,
            ),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(976, rects.base.bottom)
        assertEquals(700, rects.interactive.bottom)
        assertEquals(rects.base.top, rects.interactive.top)
        assertEquals(rects.base.left, rects.interactive.left)
        assertEquals(rects.base.right, rects.interactive.right)
    }

    /** IME 隐藏时交互矩形与基础矩形一致。 */
    @Test
    fun keepsInteractiveRectWhenImeHidden() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(navigationBars = EdgeInsets(bottom = 24)),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(rects.base, rects.interactive)
    }

    /** 浮动键盘可见但边缘占用为零时，不凭空收缩。 */
    @Test
    fun ignoresVisibleImeWithoutEdgeOccupancy() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(ime = EdgeInsets.Zero, imeVisible = true),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(rects.base, rects.interactive)
    }

    /**
     * 布局只用当前系统安全区，分类用的稳定横向边距不参与布局；
     * 左右安全区与 chrome 占用逐边相加一次，不重复扣除。
     */
    @Test
    fun combinesHorizontalInsetsAndChromeOnce() {
        val snapshot = snapshot(
            width = 1000,
            insets = WindowInsetsSnapshot(
                navigationBars = EdgeInsets(right = 30),
                stableLeftPx = 10,
                stableRightPx = 30,
            ),
        )

        val rects = ContentGeometry.compute(snapshot, AppChromeInsets(left = 88))

        assertEquals(88, rects.base.left)
        assertEquals(1000 - 30, rects.base.right)
        assertEquals(1000 - 30 - 88, rects.base.width)
    }

    /** 超大边距或过小窗口不产生负宽高。 */
    @Test
    fun clampsOversizedInsets() {
        val snapshot = snapshot(
            width = 400,
            height = 400,
            insets = WindowInsetsSnapshot(
                statusBars = EdgeInsets(top = 900),
                displayCutout = EdgeInsets(left = 900, right = 900),
            ),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(400, rects.base.left)
        assertEquals(rects.base.left, rects.base.right)
        assertEquals(400, rects.base.top)
        assertEquals(rects.base.top, rects.base.bottom)
        assertEquals(0, rects.base.width)
        assertEquals(0, rects.base.height)
    }

    /** 系统手势区不进入布局安全区：只有手势边距时内容区仍是整窗。 */
    @Test
    fun ignoresSystemGesturesForLayout() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(systemGestures = EdgeInsets(left = 40, bottom = 60)),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(0, rects.base.left)
        assertEquals(0, rects.base.top)
        assertEquals(2400, rects.base.bottom)
    }

    private fun snapshot(
        width: Int = 1080,
        height: Int = 2400,
        density: Float = 2.75f,
        insets: WindowInsetsSnapshot = WindowInsetsSnapshot(),
    ): WindowSnapshot {
        val metrics = WindowMetricsSnapshot(
            widthPx = width,
            heightPx = height,
            density = density,
            rootLeftPx = 0,
            rootTopPx = 0,
            rootWidthPx = width,
            rootHeightPx = height,
        )
        val widthDp = WindowBreakpointPolicy.stableWidthDp(width, insets.stableLeftPx, insets.stableRightPx, density)
            ?: 0f
        return WindowSnapshot(
            metrics = metrics,
            insets = insets,
            breakpointWidthDp = widthDp,
            layoutClass = WindowBreakpointPolicy.classify(widthDp),
        )
    }
}
