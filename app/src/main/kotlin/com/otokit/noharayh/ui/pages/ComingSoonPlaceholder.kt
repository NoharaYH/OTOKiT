package com.otokit.noharayh.ui.pages

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent

/**
 * 敬请期待占位：只构造自己的根 View，不引用 shell、导航或主题 Registry。
 */
object ComingSoonPlaceholder {

    fun create(context: Context, textColor: Int): PageContent {
        val text = TextView(context).apply {
            setText(R.string.placeholder_coming_soon)
            gravity = Gravity.CENTER
            setTextAppearance(android.R.style.TextAppearance_Material_Large)
            AppTypography.applyAppTypeface(this)
            setTextColor(textColor)
        }
        val root = FrameLayout(context).apply {
            addView(
                text,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
        return PageContent(root, text::setTextColor)
    }
}
