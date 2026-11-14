package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.widget.TextView

/** 无业务依赖的 View 构造工具；组件与工厂都只向下引用。 */
internal object ViewPrimitives {
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
    fun text(context: Context, value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        AppTypography.applyAppTypeface(this, bold)
    }
    fun shape(context: Context, color: Int, radius: Int) = ContinuousCornerDrawable(color, dp(context, radius).toFloat())
}
