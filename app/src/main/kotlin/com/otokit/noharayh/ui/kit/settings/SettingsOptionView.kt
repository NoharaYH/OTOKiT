package com.otokit.noharayh.ui.kit.settings

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
            setPadding(dp(12), dp(12), dp(16), dp(12))
            minimumHeight = dp(64)
            isClickable = false
        }
        val circle = FrameLayout(context).apply { background = badge }
        image.setImageResource(icon)
        circle.addView(image, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        row.addView(circle, LinearLayout.LayoutParams(dp(40), dp(40)))
        label.apply {
            text = title
            textSize = 17f
            includeFontPadding = false
            gravity = Gravity.CENTER_VERTICAL
            AppTypography.applyAppTypeface(this, bold = true)
        }
        row.addView(label, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = dp(16)
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
