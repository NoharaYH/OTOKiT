package com.otokit.noharayh.ui.kit.common

import android.view.View

/** 页面提供自己的展示根和颜色更新入口；调用方无需了解内部 View 结构。 */
class PageContent(
    val view: View,
    private val colorUpdater: (Int) -> Unit,
) {
    fun updateColor(color: Int) {
        colorUpdater(color)
    }
}
