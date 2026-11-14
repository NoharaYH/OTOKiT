package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import com.otokit.noharayh.R
import android.graphics.Color
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView

import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.text
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.shape

/** 设置页面的兼容装配入口；标准组件不反向引用此工厂。 */
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

    fun info(context: Context, label: String, value: String) = SettingsInfoRow(context, label, value)

    fun action(context: Context, label: String, icon: SettingsButton.ActionIcon = SettingsButton.ActionIcon.Next,
        deferClick: Boolean = false, onClick: () -> Unit,
    ) = SettingsButton(context, label, SettingsButton.Kind.Action, trailingIcon = icon, onClick = onClick).apply {
        deferClickUntilReleaseFeedback = deferClick
    }

    fun choice(context: Context, label: String, selected: Boolean, onClick: () -> Unit) =
        SettingsButton(context, label, SettingsButton.Kind.Choice, selected, onClick = onClick)

    fun gap(context: Context) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) }
}
