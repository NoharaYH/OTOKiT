package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import com.otokit.noharayh.R
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.kit.common.AppTypography
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 设置的纯展示积木，只接文案、尺寸、配色和点击回调。 */
internal object SettingsPageViews {
    fun placeholder(context: Context, title: String, color: Int, margin: Int, split: Boolean): View = ScrollView(context).apply {
        isFillViewport = true
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
            addView(text(context, context.getString(R.string.settings_child_placeholder, title), 16f, color).apply {
                setPadding(dp(context, 16), dp(context, 16), dp(context, 16), dp(context, 16))
                background = shape(context, Color.WHITE, 16)
            }, LinearLayout.LayoutParams(-1, -2))
        }
        addView(body, FrameLayout.LayoutParams(-1, -2))
    }

    fun card(context: Context, icon: Int, title: Int, content: View? = null, trailing: View? = null) =
        SettingsCard(context, icon, title, content, trailing)

    fun info(context: Context, label: String, value: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        minimumHeight = dp(context, SettingsCardDimensions.CONTENT_ROW_HEIGHT_DP)
        background = shape(context, 0xFFF5F5F5.toInt(), SettingsCardDimensions.CONTENT_CORNER_DP)
        addView(rowText(context, label), LinearLayout.LayoutParams(0, -1, 1f))
        addView(text(context, value, 14f, 0xFF323232.toInt(), true).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-2, -1))
    }

    fun action(context: Context, label: String, external: Boolean = false, onClick: () -> Unit) =
        SettingsButton(context, label, SettingsButton.Kind.Action, false, external, onClick)

    fun choice(context: Context, label: String, selected: Boolean, onClick: () -> Unit) =
        SettingsButton(context, label, SettingsButton.Kind.Choice, selected, false, onClick)

    private fun rowText(context: Context, value: String) = text(context, value, 14f, 0xFF616161.toInt()).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
    }

    fun text(context: Context, value: String, size: Float, color: Int, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = false
        AppTypography.applyAppTypeface(this, bold)
    }
    fun gap(context: Context) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
    fun shape(context: Context, color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(context, radius).toFloat()
    }
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + 0.5f).toInt()
}

/** 只表达外部确认的单选状态；无障碍点击仍走原点击回调，不自行修改选择。 */
internal fun PressSurface.configureSingleChoiceSemantics(selected: Boolean, column: Int? = null) {
    isSelected = selected
    accessibilityDelegate = object : View.AccessibilityDelegate() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.className = "android.widget.RadioButton"
            info.isCheckable = true
            info.isChecked = host.isSelected
            if (column != null) {
                info.collectionItemInfo = AccessibilityNodeInfo.CollectionItemInfo.obtain(
                    0, 1, column, 1, false, host.isSelected)
            }
        }
    }
}
