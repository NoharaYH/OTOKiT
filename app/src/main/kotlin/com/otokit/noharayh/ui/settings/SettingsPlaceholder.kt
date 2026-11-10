package com.otokit.noharayh.ui.settings

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent

/**
 * 设置占位的展示入口：浅底容器 + 居中占位文字。
 *
 * 只负责自己的排版与底色解析；不接设置业务、不持有 shell 或导航状态。
 */
object SettingsPlaceholder {

    fun create(context: Context, textColor: Int): PageContent {
        val container = FrameLayout(context).apply {
            isClickable = true
            isFocusable = true
            isFocusableInTouchMode = true
            setBackgroundColor(resolveColorBackground(context))
        }
        val text = placeholderText(context, R.string.placeholder_settings, textColor)
        container.addView(
            text,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        return PageContent(container, text::setTextColor)
    }

    private fun placeholderText(context: Context, textRes: Int, textColor: Int): TextView =
        TextView(context).apply {
            setText(textRes)
            gravity = Gravity.CENTER
            setTextAppearance(android.R.style.TextAppearance_Material_Large)
            AppTypography.applyAppTypeface(this)
            // 颜色放在 TextAppearance 与字体之后，避免被前者覆盖。
            setTextColor(textColor)
        }

    private fun resolveColorBackground(context: Context): Int {
        val value = TypedValue()
        if (!context.theme.resolveAttribute(android.R.attr.colorBackground, value, true)) {
            return 0
        }
        return if (value.resourceId != 0) ContextCompat.getColor(context, value.resourceId) else value.data
    }
}
