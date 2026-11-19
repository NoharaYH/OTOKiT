package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.RectF
import android.view.Gravity
import android.widget.ImageView
import androidx.core.graphics.withScale
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.dimensions.InfoIconDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

/** 设置容器中的独立说明操作。图标轻按内收，点击区由组件尺寸保持。 */
internal class InfoIconButton(context: Context, description: CharSequence, onClick: () -> Unit) : PressSurface(context) {
    private val icon = ImageView(context).apply {
        setImageResource(R.drawable.ic_info_outline_24)
        setColorFilter(Colors.TEXT)
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override val rippleCenterX: Float get() = icon.left + icon.width / 2f

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
        val iconSize = dp(context, Dimensions.ICON_DP)
        addView(icon, LayoutParams(iconSize, iconSize, Gravity.START or Gravity.CENTER_VERTICAL).apply {
            marginStart = dp(context, Dimensions.TEXT_GAP_DP)
        })
        setOnClickListener { onClick() }
    }

    override fun dispatchDraw(canvas: Canvas) {
        val scale = 1f - 2f * pressInset * pressFraction / minimumWidth
        canvas.withScale(scale, scale, rippleCenterX, height / 2f) { super.dispatchDraw(this) }
    }

    override fun buildSurfacePath(path: Path, bounds: RectF, top: Float, bottom: Float) {
        path.rewind()
        path.addCircle(rippleCenterX, bounds.centerY(), bounds.height() / 2f, Path.Direction.CW)
    }
}
