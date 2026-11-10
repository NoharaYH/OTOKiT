package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.window.PixelRect
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

        val rects = ContentGeometry.compute(snapshot, ChromeInsets(top = 72))

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

    /** 背景底边越过导航栏到 1000px；IME 300px 只把交互底边压到 700px。 */
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

        assertEquals(1000, rects.base.bottom)
        assertEquals(700, rects.interactive.bottom)
        assertEquals(rects.base.top, rects.interactive.top)
        assertEquals(rects.base.left, rects.interactive.left)
        assertEquals(rects.base.right, rects.interactive.right)
    }

    /** 背景画到导航栏下沿，交互矩形仍避让导航栏 24px。 */
    @Test
    fun keepsInteractiveAboveNavigationBar() {
        val snapshot = snapshot(
            insets = WindowInsetsSnapshot(navigationBars = EdgeInsets(bottom = 24)),
        )

        val rects = ContentGeometry.compute(snapshot)

        assertEquals(2400, rects.base.bottom)
        assertEquals(2376, rects.interactive.bottom)
        assertEquals(rects.base.top, rects.interactive.top)
        assertEquals(rects.base.left, rects.interactive.left)
        assertEquals(rects.base.right, rects.interactive.right)
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

        val rects = ContentGeometry.compute(snapshot, ChromeInsets(left = 88))

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

    /** 根被键盘缩小后，背景仍用窗口基础尺寸，只有内容/覆盖层使用交互尺寸。 */
    @Test
    fun keepsBackgroundSizeWhenRootShrinksForIme() {
        val before = snapshot(
            width = 1000,
            height = 1000,
            insets = WindowInsetsSnapshot(
                statusBars = EdgeInsets(top = 24),
                navigationBars = EdgeInsets(bottom = 24),
            ),
        )
        val resized = before.copy(
            metrics = before.metrics.copy(rootHeightPx = 700),
            insets = before.insets.copy(ime = EdgeInsets(bottom = 300), imeVisible = true),
        )

        val beforeBase = rootRect(ContentGeometry.compute(before).base, before)
        val resizedRects = ContentGeometry.compute(resized)
        val background = rootRect(resizedRects.base, resized)
        val interactive = rootRect(resizedRects.interactive, resized)

        assertEquals(beforeBase, background)
        assertEquals(PixelRect(0, 24, 1000, 1000), background)
        assertEquals(976, background.height)
        assertEquals(PixelRect(0, 24, 1000, 700), interactive)
        assertEquals(676, interactive.height)
    }

    /** 窗口矩形和根尺寸不变、仅根原点移动时，局部边界必须随之更新。 */
    @Test
    fun updatesLocalRectWhenOnlyRootOriginChanges() {
        val before = snapshot(
            insets = WindowInsetsSnapshot(displayCutout = EdgeInsets(left = 50, top = 24)),
        )
        val moved = before.copy(metrics = before.metrics.copy(rootLeftPx = 20, rootTopPx = 10))
        val windowRect = ContentGeometry.compute(before).base

        assertEquals(windowRect, ContentGeometry.compute(moved).base)
        val beforeLocal = rootRect(windowRect, before)
        val movedLocal = rootRect(windowRect, moved)

        assertEquals(50, beforeLocal.left)
        assertEquals(24, beforeLocal.top)
        assertEquals(30, movedLocal.left)
        assertEquals(14, movedLocal.top)
        assertEquals(beforeLocal.width, movedLocal.width)
        assertEquals(beforeLocal.height, movedLocal.height)
    }

    /** 根原点越过内容起点时允许负坐标，不能通过夹零改变矩形覆盖的窗口区域。 */
    @Test
    fun preservesNegativeLocalCoordinates() {
        val local = ContentGeometry.relativeToRoot(PixelRect(10, 24, 500, 976), 30, 100)

        assertEquals(PixelRect(-20, -76, 470, 876), local)
        assertEquals(490, local.width)
        assertEquals(952, local.height)
    }

    /** 退化内容区保持零尺寸，不因坐标变换填满根布局。 */
    @Test
    fun preservesEmptyRectSizeInRootCoordinates() {
        val local = ContentGeometry.relativeToRoot(PixelRect(400, 400, 400, 400), 50, 30)

        assertEquals(PixelRect(350, 370, 350, 370), local)
        assertEquals(0, local.width)
        assertEquals(0, local.height)
    }

    private fun rootRect(rect: PixelRect, snapshot: WindowSnapshot): PixelRect =
        ContentGeometry.relativeToRoot(rect, snapshot.metrics.rootLeftPx, snapshot.metrics.rootTopPx)

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
