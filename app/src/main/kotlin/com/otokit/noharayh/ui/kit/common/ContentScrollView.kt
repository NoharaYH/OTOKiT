package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.widget.ScrollView

/** 标准内容滚动表面关闭 Android 原生横、纵滚动条。 */
internal open class ContentScrollView(context: Context) : ScrollView(context) {
    init {
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
    }
}
