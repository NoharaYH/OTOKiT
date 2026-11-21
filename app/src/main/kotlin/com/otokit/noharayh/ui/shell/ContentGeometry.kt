package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.chrome.ChromeLayoutPolicy
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.relativeToRoot

/** 区分背景、交互边界与滚动绘制面；滚动面通过内部留白约束终点，不在手势区上沿硬裁。 */
data class ContentRects(
    val base: PixelRect,
    val interactive: PixelRect,
    val scrollable: PixelRect,
    val navigation: PixelRect = interactive,
)

/**
 * 内容矩形的纯计算。
 *
 * 先扣合并后的系统安全区，再扣应用占用；底部避让手势区，同一边不重复相加。
 * IME 收缩交互与滚动绘制矩形，不改变基础矩形。
 */
object ContentGeometry {

    /** 窗口矩形转为根局部坐标；保留原尺寸和负坐标，父容器只负责裁剪。 */
    internal fun relativeToRoot(rect: PixelRect, rootLeftPx: Int, rootTopPx: Int): PixelRect =
        rect.relativeToRoot(rootLeftPx, rootTopPx)

    fun compute(snapshot: WindowSnapshot, chrome: ChromeInsets = ChromeInsets.Zero): ContentRects {
        val width = snapshot.metrics.widthPx
        val height = snapshot.metrics.heightPx
        val safe = snapshot.insets.systemSafeEdges(
            reserveStatusBarTop = !ChromeLayoutPolicy.usesRail(snapshot.layoutClass),
        )
        val backgroundSafe = snapshot.insets.backgroundSafeEdges

        val left = (safe.left + chrome.left).coerceIn(0, width)
        val top = (safe.top + chrome.top).coerceIn(0, height)
        val right = (width - safe.right - chrome.right).coerceIn(left, width)
        // 背景延伸到导航栏下沿：手势条透明时不再留白色空带。
        val backgroundBottom = (height - backgroundSafe.bottom - chrome.bottom).coerceIn(top, height)
        // 左右返回手势不缩窄页面；底部滚动终点及操作区必须停在手势保护区上方。
        val bottomSafe = maxOf(safe.bottom, snapshot.insets.systemGestures.bottom,
            snapshot.insets.mandatorySystemGestures.bottom)
        val contentBottom = (height - bottomSafe - chrome.bottom).coerceIn(top, backgroundBottom)
        val base = PixelRect(left, top, right, backgroundBottom)

        val interactiveBottom = if (snapshot.insets.imeHasEdgeOccupancy) {
            val imeTop = (height - snapshot.insets.ime.bottom).coerceIn(base.top, contentBottom)
            minOf(contentBottom, imeTop)
        } else {
            contentBottom
        }
        val scrollableBottom = if (snapshot.insets.imeHasEdgeOccupancy) {
            (height - snapshot.insets.ime.bottom).coerceIn(base.top, base.bottom)
        } else base.bottom

        return ContentRects(
            base = base,
            interactive = PixelRect(base.left, base.top, base.right, interactiveBottom),
            scrollable = PixelRect(base.left, base.top, base.right, scrollableBottom),
        )
    }
}
