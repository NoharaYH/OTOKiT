package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.otokit.noharayh.R
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

    fun card(context: Context, icon: Int, title: Int, content: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(context, Color.WHITE, 16)
        val pad = dp(context, 12)
        setPadding(pad, pad, pad, pad)
        val header = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = dp(context, 28) }
        header.addView(ImageView(context).apply { setImageResource(icon); setColorFilter(0xFF616161.toInt()) }, LinearLayout.LayoutParams(dp(context, 22), dp(context, 22)))
        header.addView(text(context, context.getString(title), 16f, 0xFF323232.toInt(), true).apply { letterSpacing = 0.07f },
            LinearLayout.LayoutParams(-2, -2).apply { marginStart = dp(context, 8) })
        addView(header, LinearLayout.LayoutParams(-1, -2))
        addView(content, LinearLayout.LayoutParams(-1, -2).apply { topMargin = pad })
    }

    fun info(context: Context, label: String, value: String) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
        minimumHeight = dp(context, 48)
        background = shape(context, 0xFFF5F5F5.toInt(), 16)
        addView(text(context, label, 14f, 0xFF757575.toInt()), LinearLayout.LayoutParams(0, -2, 1f))
        addView(text(context, value, 14f, 0xFF323232.toInt(), true))
    }

    fun action(context: Context, label: String, subtitle: String?, onClick: () -> Unit) = PressSurface(context).apply {
        fillColor = 0xFFF5F5F5.toInt()
        contentDescription = if (subtitle == null) label else "$label，$subtitle"
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumHeight = dp(context, 48)
            setPadding(dp(context, 16), dp(context, 14), dp(context, 16), dp(context, 14))
            val titleRow = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            titleRow.addView(text(context, label, 15f, 0xFF616161.toInt(), true), LinearLayout.LayoutParams(0, -2, 1f))
            val iconSize = dp(context, if (subtitle == null) 20 else 16)
            titleRow.addView(ImageView(context).apply {
                setImageResource(if (subtitle == null) R.drawable.ic_chevron_right_24 else R.drawable.ic_open_in_new_16)
                setColorFilter(0xFFBDBDBD.toInt())
            }, LinearLayout.LayoutParams(iconSize, iconSize).apply { if (subtitle != null) marginStart = dp(context, 4) })
            addView(titleRow, LinearLayout.LayoutParams(-1, -2))
            if (subtitle != null) addView(text(context, subtitle, 12f, 0xFF9E9E9E.toInt()), LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 4) })
        }
        addView(column, FrameLayout.LayoutParams(-1, -2))
        setOnClickListener { onClick() }
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
