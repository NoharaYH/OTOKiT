package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.widget.ScrollView

/** 宿主保留边到边的绘制高度，滚动内容自行为交互底边留出空间。 */
internal interface EdgeToEdgeScrollable {
    fun setBottomSafeInset(bottom: Int)
}

/** 标准滚动表面延伸到底边；安全区只影响滚动终点，不裁短绘制视口。 */
internal open class ContentScrollView(context: Context) : ScrollView(context), EdgeToEdgeScrollable {
    private var appliedBottomSafeInset = 0

    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
    }

    override fun setBottomSafeInset(bottom: Int) {
        if (appliedBottomSafeInset == bottom) return
        val contentPadding = paddingBottom - appliedBottomSafeInset
        appliedBottomSafeInset = bottom
        setPadding(paddingLeft, paddingTop, paddingRight, contentPadding + bottom)
    }
}
