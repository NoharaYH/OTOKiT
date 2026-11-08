package com.otokit.noharayh.ui.shell

import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot

/**
 * chrome 上报的应用额外占用，不含系统安全区。
 *
 * 首批交付没有 chrome，实际传入 [Zero]；非零值只用于验证 06/08 已约定的几何契约。
 */
data class AppChromeInsets(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0,
) {
    companion object {
        val Zero: AppChromeInsets = AppChromeInsets()
    }
}

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
    internal fun relativeToRoot(rect: PixelRect, rootLeftPx: Int, rootTopPx: Int): PixelRect = PixelRect(
        left = rect.left - rootLeftPx,
        top = rect.top - rootTopPx,
        right = rect.right - rootLeftPx,
        bottom = rect.bottom - rootTopPx,
    )

    fun compute(snapshot: WindowSnapshot, chrome: AppChromeInsets = AppChromeInsets.Zero): ContentRects {
        val width = snapshot.metrics.widthPx
        val height = snapshot.metrics.heightPx
        val safe = snapshot.insets.systemSafeEdges

        val left = (safe.left + chrome.left).coerceIn(0, width)
        val top = (safe.top + chrome.top).coerceIn(0, height)
        val right = (width - safe.right - chrome.right).coerceIn(left, width)
        val bottom = (height - safe.bottom - chrome.bottom).coerceIn(top, height)
        val base = PixelRect(left, top, right, bottom)

        val interactiveBottom = if (snapshot.insets.imeHasEdgeOccupancy) {
            val imeTop = (height - snapshot.insets.ime.bottom).coerceIn(base.top, base.bottom)
            minOf(base.bottom, imeTop)
        } else {
            base.bottom
        }

        return ContentRects(
            base = base,
            interactive = PixelRect(base.left, base.top, base.right, interactiveBottom),
        )
    }
}
