package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.text

/** 复合容器标题右侧的简短状态与图标。 */
internal class SettingsHeaderStatus(context: Context) : LinearLayout(context) {
    private val label = text(context, "", Dimensions.HEADER_STATUS_TEXT_SP, SettingsComponentColors.TEXT).apply {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
    }
    private val icon = ImageView(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    init {
        gravity = Gravity.END or Gravity.CENTER_VERTICAL
        addView(label, LayoutParams(0, -2, 1f))
        val iconSize = dp(context, Dimensions.HEADER_STATUS_ICON_DP)
        addView(icon, LayoutParams(iconSize, iconSize).apply {
            marginStart = dp(context, Dimensions.HEADER_STATUS_ICON_GAP_DP)
        })
    }

    fun render(value: String, @DrawableRes iconRes: Int, @ColorInt color: Int) {
        label.text = value
        label.setTextColor(color)
        icon.setImageResource(iconRes)
        icon.setColorFilter(color)
    }
}
