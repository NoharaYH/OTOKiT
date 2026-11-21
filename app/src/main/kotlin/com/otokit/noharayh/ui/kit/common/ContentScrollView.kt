package com.otokit.noharayh.ui.kit.common

import android.content.Context

/** 安全区属于滚动内容的首尾留白，列表可以画到两端。 */
internal interface EdgeToEdgeScrollable {
    fun setSafeInsets(top: Int, bottom: Int)
}

/** 标准滚动表面延伸到底边；安全区只影响滚动终点，不裁短绘制视口。 */
internal open class ContentScrollView(context: Context, edgeOvershootPx: Float = 0f, horizontalDrawingOutsetPx: Int = 0) :
    EdgePullScrollView(context, edgeOvershootPx, pullWhenContentFits = edgeOvershootPx > 0f,
        horizontalDrawingOutsetPx = horizontalDrawingOutsetPx), EdgeToEdgeScrollable {
    private var appliedTopSafeInset = 0
    private var appliedBottomSafeInset = 0

    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
    }

    override fun setSafeInsets(top: Int, bottom: Int) {
        if (appliedTopSafeInset == top && appliedBottomSafeInset == bottom) return
        setPadding(paddingLeft, paddingTop - appliedTopSafeInset + top,
            paddingRight, paddingBottom - appliedBottomSafeInset + bottom)
        appliedTopSafeInset = top
        appliedBottomSafeInset = bottom
    }
}
