package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot
import com.otokit.noharayh.ui.window.relativeToRoot

/** 基础内容矩形（背景使用）与避让 IME 后的交互矩形（内容/覆盖层使用）。 */
data class ContentRects(
    val base: PixelRect,
    val interactive: PixelRect,
)

/**
 * 内容矩形的纯计算。
 *
 * 先扣合并后的系统安全区，再扣应用占用；同一边不重复相加。
 * IME 只收缩交互矩形，不改变基础矩形。
 */
object ContentGeometry {

    /** 窗口矩形转为根局部坐标；保留原尺寸和负坐标，父容器只负责裁剪。 */
    internal fun relativeToRoot(rect: PixelRect, rootLeftPx: Int, rootTopPx: Int): PixelRect =
        rect.relativeToRoot(rootLeftPx, rootTopPx)

    fun compute(snapshot: WindowSnapshot, chrome: ChromeInsets = ChromeInsets.Zero): ContentRects {
        val width = snapshot.metrics.widthPx
        val height = snapshot.metrics.heightPx
        val safe = snapshot.insets.systemSafeEdges
        val backgroundSafe = snapshot.insets.backgroundSafeEdges

        val left = (safe.left + chrome.left).coerceIn(0, width)
        val top = (safe.top + chrome.top).coerceIn(0, height)
        val right = (width - safe.right - chrome.right).coerceIn(left, width)
        // 背景延伸到导航栏下沿：手势条透明时不再留白色空带。
        val backgroundBottom = (height - backgroundSafe.bottom - chrome.bottom).coerceIn(top, height)
        val contentBottom = (height - safe.bottom - chrome.bottom).coerceIn(top, backgroundBottom)
        val base = PixelRect(left, top, right, backgroundBottom)

        val interactiveBottom = if (snapshot.insets.imeHasEdgeOccupancy) {
            val imeTop = (height - snapshot.insets.ime.bottom).coerceIn(base.top, contentBottom)
            minOf(contentBottom, imeTop)
        } else {
            contentBottom
        }

        return ContentRects(
            base = base,
            interactive = PixelRect(base.left, base.top, base.right, interactiveBottom),
        )
    }
}
