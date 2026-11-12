package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.choice
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.dp


internal object AppSettingsPage {

    fun create(
        context: Context,
        margin: Int,
        split: Boolean,
        displayScale: DisplayScaleOption,
        onDisplayScaleSelected: (DisplayScaleOption) -> Unit,
    ): View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
        }

        // 行按固定顺序布局；按压行由容器提升为最后绘制，外环不被相邻行盖住。
        val choices = PressFrontColumn(context)
        DisplayScaleOption.entries.forEach { option ->
            choices.addView(
                choice(
                    context = context,
                    label = context.getString(option.labelRes()),
                    selected = option == displayScale,
                    onClick = { onDisplayScaleSelected(option) },
                ),
                LinearLayout.LayoutParams(-1, -2).apply {
                    if (option != DisplayScaleOption.entries.first()) topMargin = dp(context, 4)
                },
            )
        }
        body.addView(
            card(context, R.drawable.ic_aspect_ratio_24, R.string.settings_display_scale, choices),
        )

        return ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private fun DisplayScaleOption.labelRes(): Int = when (this) {
        DisplayScaleOption.Small -> R.string.settings_display_scale_small
        DisplayScaleOption.Standard -> R.string.settings_display_scale_standard
        DisplayScaleOption.Large -> R.string.settings_display_scale_large
    }
}
