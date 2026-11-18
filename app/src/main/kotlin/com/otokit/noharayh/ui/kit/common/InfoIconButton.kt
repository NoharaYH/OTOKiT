package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.view.Gravity
import android.widget.ImageView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.InfoIconDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

/** 设置行的独立说明操作：48dp 热区，中心黑色反馈，不触发行的五项效果。 */
internal class InfoIconButton(context: Context, description: CharSequence, onClick: () -> Unit) : PressSurface(context) {
    init {
        minimumWidth = dp(context, Dimensions.TOUCH_DP)
        minimumHeight = minimumWidth
        fillColor = Colors.SURFACE
        baseRadius = minimumWidth / 2f
        pressInset = 0f
        whiteMaxAlpha = 0f
        centeredRipple = true
        rippleMaxDiameter = dp(context, Dimensions.RIPPLE_MAX_DP).toFloat()
        rippleRestDiameter = dp(context, Dimensions.RIPPLE_REST_DP).toFloat()
        deferClickUntilReleaseFeedback = true
        contentDescription = description
        isFocusable = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_info_outline_24)
            setColorFilter(Colors.TEXT)
            importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LayoutParams(dp(context, Dimensions.ICON_DP), dp(context, Dimensions.ICON_DP), Gravity.CENTER))
        setOnClickListener { onClick() }
    }
}
