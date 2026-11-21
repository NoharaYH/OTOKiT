package com.otokit.noharayh.ui.chrome.floating

import com.otokit.noharayh.ui.chrome.ChromeInsets
import com.otokit.noharayh.ui.shell.ContentGeometry
import com.otokit.noharayh.ui.shell.ContentRects
import com.otokit.noharayh.ui.window.PixelRect
import com.otokit.noharayh.ui.window.WindowSnapshot

internal object FloatingGeometry {
    fun compute(window: WindowSnapshot, insets: ChromeInsets): ContentRects {
        val pages = ContentGeometry.compute(window, insets.copy(bottom = 0))
        val height = window.metrics.heightPx
        val bottom = height - if (window.insets.imeHasEdgeOccupancy) window.insets.ime.bottom else 0
        val top = maxOf(window.insets.captionBar.top, window.insets.navigationBars.top).coerceIn(0, height)
        return pages.copy(
            base = pages.base.copy(top = top),
            scrollable = pages.scrollable.copy(top = top),
            navigation = PixelRect(0, top, window.metrics.widthPx, bottom.coerceAtLeast(top)),
            interactive = pages.interactive.copy(bottom = (bottom - insets.bottom)
                .coerceIn(pages.interactive.top, pages.interactive.bottom)),
        )
    }
}
