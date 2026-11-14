package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.constants.dimensions.SettingsMenuDimensions as Dimensions
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 旧设置的 64dp 布局，内容区统一按压容器；反色只呈现外部进度。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组装，需要明确文案与配色。
class SettingsOptionView(context: Context, title: CharSequence, icon: Int, private val accent: Int) :
    PressSurface(context) {
    private val badge = GradientDrawable().apply { shape = GradientDrawable.OVAL }
    private val image = ImageView(context)
    private val label = TextView(context)

    init {
        outerHaloEnabled = true
        contentDescription = title
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Dimensions.INSET_DP), dp(Dimensions.INSET_DP), dp(Dimensions.END_INSET_DP), dp(Dimensions.INSET_DP))
            minimumHeight = dp(Dimensions.HEIGHT_DP)
            isClickable = false
        }
        val circle = FrameLayout(context).apply { background = badge }
        image.setImageResource(icon)
        circle.addView(image, FrameLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP), Gravity.CENTER))
        row.addView(circle, LinearLayout.LayoutParams(dp(Dimensions.BADGE_DP), dp(Dimensions.BADGE_DP)))
        label.apply {
            text = title
            textSize = Dimensions.TEXT_SP
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            AppTypography.applyAppTypeface(this, bold = true)
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(Dimensions.TEXT_GAP_DP)
        })
        addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        setSelectionProgress(0f)
    }

    fun setSelectionProgress(value: Float) {
        val p = value.coerceIn(0f, 1f)
        fillColor = ColorUtils.blendARGB(Color.WHITE, accent, p)
        badge.setColor(ColorUtils.blendARGB(accent, Color.WHITE, p))
        image.setColorFilter(ColorUtils.blendARGB(Color.WHITE, accent, p))
        label.setTextColor(ColorUtils.blendARGB(accent, Color.WHITE, p))
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()
}
