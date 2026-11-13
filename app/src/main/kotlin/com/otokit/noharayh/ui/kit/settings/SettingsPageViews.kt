package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.otokit.noharayh.R
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

    fun card(context: Context, icon: Int, title: Int, content: View, trailing: View? = null) = LinearLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        orientation = LinearLayout.VERTICAL
        background = shape(context, Color.WHITE, SettingsCardDimensions.CORNER_DP)
        val pad = dp(context, SettingsCardDimensions.CONTENT_INSET_DP)
        setPadding(pad, pad, pad, pad)
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 28)
            clipChildren = false
            clipToPadding = false
        }
        header.addView(ImageView(context).apply { setImageResource(icon); setColorFilter(0xFF616161.toInt()) }, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))
        header.addView(text(context, context.getString(title), 16f, 0xFF323232.toInt(), true).apply { letterSpacing = 0.07f },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 8) })
        if (trailing != null) {
            header.addView(trailing, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dp(context, 8) })
        }
        addView(header, LinearLayout.LayoutParams(-1, -2))
        addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad })
    }

    fun info(context: Context, label: String, value: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 16), 0, dp(context, 16), 0)
        minimumHeight = dp(context, SettingsCardDimensions.CONTENT_ROW_HEIGHT_DP)
        background = shape(context, 0xFFF5F5F5.toInt(), SettingsCardDimensions.CONTENT_CORNER_DP)
        addView(rowText(context, label), LinearLayout.LayoutParams(0, -1, 1f))
        addView(text(context, value, 14f, 0xFF323232.toInt(), true).apply { gravity = Gravity.CENTER_VERTICAL }, LinearLayout.LayoutParams(-2, -1))
    }

    fun action(context: Context, label: String, external: Boolean = false, onClick: () -> Unit) = PressSurface(context).apply {
        outerHaloEnabled = true
        deferClickUntilReleaseFeedback = external
        fillColor = 0xFFF5F5F5.toInt()
        baseRadius = dp(context, SettingsCardDimensions.CONTENT_CORNER_DP).toFloat()
        contentDescription = label
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            addView(rowText(context, label), LinearLayout.LayoutParams(0, -1, 1f))
            addView(ImageView(context).apply {
                setImageResource(if (external) R.drawable.ic_open_in_new_24 else R.drawable.ic_chevron_right_24)
                setColorFilter(0xFFBDBDBD.toInt())
            }, LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)).apply { marginStart = dp(context, 4) })
        }
        addView(row, FrameLayout.LayoutParams(-1, dp(context, SettingsCardDimensions.CONTENT_ROW_HEIGHT_DP)))
        setOnClickListener { onClick() }
    }

    /** 单选行：与 action 行同一按压容器，右侧为圆形选择指示。 */
    fun choice(context: Context, label: String, selected: Boolean, onClick: () -> Unit) = PressSurface(context).apply {
        configureSingleChoiceSemantics(selected)
        outerHaloEnabled = true
        fillColor = 0xFFF5F5F5.toInt()
        baseRadius = dp(
            context,
            if (selected) {
                SettingsCardDimensions.CONTENT_SELECTED_CORNER_DP
            } else {
                SettingsCardDimensions.CONTENT_CORNER_DP
            },
        ).toFloat()
        contentDescription = label
        val row = LinearLayout(context).apply {
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 16), 0, dp(context, 16), 0)
            addView(rowText(context, label).apply {
                setTextColor(if (selected) 0xFF2196F3.toInt() else 0xFF616161.toInt())
            }, LinearLayout.LayoutParams(0, -1, 1f))
            addView(radioIndicator(context, selected), LinearLayout.LayoutParams(dp(context, 20), dp(context, 20)))
        }
        addView(row, FrameLayout.LayoutParams(-1, dp(context, SettingsCardDimensions.CONTENT_ROW_HEIGHT_DP)))
        setOnClickListener { onClick() }
    }

    private fun radioIndicator(context: Context, selected: Boolean) = FrameLayout(context).apply {
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(context, 2), if (selected) 0xFF2196F3.toInt() else 0xFFBDBDBD.toInt())
        }
        if (selected) {
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(0xFF2196F3.toInt())
                }
            }, FrameLayout.LayoutParams(dp(context, 10), dp(context, 10), Gravity.CENTER))
        }
    }

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
