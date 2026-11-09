package com.otokit.noharayh.ui.pages.pageScoreSync

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PageContent
import com.otokit.noharayh.ui.kit.common.PressSurface
import kotlin.math.roundToInt

/**
 * 成绩同步占位。
 *
 * 只构造自己的根 View，不引用 shell、不读取或保存导航状态。
 */
object ScoreSyncPlaceholder {

    /**
     * @param textColor 由 shell 提供的当前主题展示色；
     * 这里不查询主题，也不持有 Registry。
     */
    fun create(context: Context, textColor: Int): PageContent {
        val density = context.resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).roundToInt()

        val text = TextView(context).apply {
            setText(R.string.placeholder_score_sync)
            gravity = Gravity.CENTER
            setTextAppearance(android.R.style.TextAppearance_Material_Large)
            AppTypography.applyAppTypeface(this)
            setTextColor(textColor)
        }
        val buttonLabel = context.getString(R.string.press_surface_preview_button)
        val button = PressSurface(context).apply {
            // 按钮放大到能完整容纳 150dp 黑圆。
            minimumWidth = dp(260)
            contentDescription = buttonLabel
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
            setOnClickListener {
                Toast.makeText(context, R.string.press_surface_preview_clicked, Toast.LENGTH_SHORT).show()
            }
            addView(
                TextView(context).apply {
                    this.text = buttonLabel
                    gravity = Gravity.CENTER
                    setTextAppearance(android.R.style.TextAppearance_Material_Button)
                    AppTypography.applyAppTypeface(this)
                    setTextColor(Color.WHITE)
                    setPadding(dp(24), 0, dp(24), 0)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER,
                ),
            )
        }
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(
                text,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
            addView(
                button,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(160)).apply {
                    topMargin = dp(24)
                },
            )
        }
        return PageContent(root, text::setTextColor)
    }
}
