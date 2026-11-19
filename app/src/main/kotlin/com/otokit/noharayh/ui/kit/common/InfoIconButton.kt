package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Gravity
import android.widget.ImageView
import androidx.core.graphics.withScale
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.InfoIconDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

/** 设置容器中的独立说明操作。图标轻按内收，点击区由组件尺寸保持。 */
internal class InfoIconButton(context: Context, description: CharSequence, onClick: () -> Unit) : PressSurface(context) {
    init {
        minimumWidth = dp(context, Dimensions.TOUCH_DP)
        minimumHeight = minimumWidth
        fillColor = Color.TRANSPARENT
        baseRadius = minimumWidth / 2f
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

    override fun dispatchDraw(canvas: Canvas) {
        val scale = 1f - 2f * pressInset * pressFraction / minimumWidth
        canvas.withScale(scale, scale, width / 2f, height / 2f) { super.dispatchDraw(this) }
    }
}
