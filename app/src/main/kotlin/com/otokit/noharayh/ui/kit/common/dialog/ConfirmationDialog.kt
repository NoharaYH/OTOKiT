package com.otokit.noharayh.ui.kit.common.dialog

import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.otokit.noharayh.ui.constants.dimensions.ConfirmationDialogDimensions as Dimensions
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.PressSurface
import kotlin.math.roundToInt

/** 应用通用确认框；仅呈现传入内容，不持有设置策略或持久状态。 */
internal class ConfirmationDialog(
    context: Context,
    iconRes: Int,
    message: CharSequence,
    confirmLabel: String,
    cancelLabel: String,
    private val onConfirmed: () -> Unit,
) : Dialog(context) {
    private val warningColor = 0xFFE57373.toInt()

    init {
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            clipChildren = false
            clipToPadding = false
            val inset = dp(Dimensions.CONTENT_INSET_DP)
            setPadding(inset, inset, inset, inset)
            background = ContinuousCornerDrawable(Color.WHITE, dp(Dimensions.CORNER_DP).toFloat())
        }
        body.addView(ImageView(context).apply {
            setImageResource(iconRes)
            setColorFilter(warningColor)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, LinearLayout.LayoutParams(dp(Dimensions.ICON_SIZE_DP), dp(Dimensions.ICON_SIZE_DP)))
        body.addView(TextView(context).apply {
            text = message
            textSize = 16f
            setTextColor(0xFF616161.toInt())
            includeFontPadding = false
            setLineSpacing(dp(4).toFloat(), 1f)
            AppTypography.applyAppTypeface(this)
        }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.CONTENT_GAP_DP) })
        val actions = PressFrontColumn(context).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(button(confirmLabel, warningColor) {
            if (isShowing) { dismiss(); onConfirmed() }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        actions.addView(button(cancelLabel, 0xFF757575.toInt()) { dismiss() },
            LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(Dimensions.BUTTON_GAP_DP) })
        body.addView(actions, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(Dimensions.CONTENT_GAP_DP)
        })
        setContentView(body)
        setCanceledOnTouchOutside(true)
        window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.32f)
            decorView.elevation = 0f
        }
    }

    fun showWithin(availableWidth: Int) {
        show()
        window?.setLayout(
            minOf(dp(Dimensions.MAX_WIDTH_DP), (availableWidth - dp(Dimensions.WINDOW_MARGIN_DP * 2)).coerceAtLeast(1)),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun button(label: String, color: Int, onClick: () -> Unit) = PressSurface(context).apply {
        outerHaloEnabled = true
        deferClickUntilReleaseFeedback = true
        fillColor = 0xFFF5F5F5.toInt()
        baseRadius = dp(Dimensions.BUTTON_RADIUS_DP).toFloat()
        contentDescription = label
        addView(TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(color)
            gravity = Gravity.CENTER
            includeFontPadding = false
            AppTypography.applyAppTypeface(this, bold = true)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, FrameLayout.LayoutParams(-1, dp(Dimensions.BUTTON_HEIGHT_DP)))
        setOnClickListener { onClick() }
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
