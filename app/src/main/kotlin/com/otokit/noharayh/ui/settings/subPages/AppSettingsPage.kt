package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.text.Annotation
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.common.dialog.ConfirmationDialog
import com.otokit.noharayh.ui.constants.dimensions.GroupedSwitchDimensions
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.choice
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.dp
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.text


@android.annotation.SuppressLint("ViewConstructor")
internal class AppSettingsPage(
    context: Context,
    margin: Int,
    split: Boolean,
    displayScale: DisplayScaleOption,
    onDisplayScaleSelected: (DisplayScaleOption) -> Unit,
    rendering: BackgroundRenderingOptions,
    private val onHardwareAccelerationSelected: (Boolean) -> Unit,
    onHtmlAnimationsSelected: (Boolean) -> Unit,
    private val takeHardwareWarning: () -> Boolean,
) : ScrollView(context) {
    private var warningDialog: ConfirmationDialog? = null
    private val hardwareSwitch = GroupedSwitchRow(context,
        context.getString(R.string.settings_background_hardware_acceleration), true, false,
        rendering.hardwareAccelerated, ::requestHardwareAcceleration,
        labelIconRes = R.drawable.ic_error_40)
    private val animationSwitch = GroupedSwitchRow(context,
        context.getString(R.string.settings_background_html_animations), false, true,
        rendering.htmlAnimations, onHtmlAnimationsSelected)

    init {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
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
        val warningColor = 0xFFE57373.toInt()
        val warning = LinearLayout(context).apply {
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            addView(text(context, context.getString(R.string.settings_display_scale_warning), 11f, warningColor).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_error_40)
                setColorFilter(warningColor)
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)).apply {
                marginStart = dp(context, 4)
            })
        }
        body.addView(card(context, R.drawable.ic_aspect_ratio_24,
            R.string.settings_display_scale, choices, trailing = warning))
        val switches = PressFrontColumn(context).apply {
            addView(hardwareSwitch, LinearLayout.LayoutParams(-1, -2))
            addView(animationSwitch, LinearLayout.LayoutParams(-1, -2).apply {
                topMargin = dp(context, GroupedSwitchDimensions.GAP_DP)
            })
        }
        body.addView(switches, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(context, 12) })
        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        addView(body, FrameLayout.LayoutParams(-1, -2))
    }

    fun renderBackgroundRendering(options: BackgroundRenderingOptions) {
        hardwareSwitch.isChecked = options.hardwareAccelerated
        animationSwitch.isChecked = options.htmlAnimations
    }

    private fun requestHardwareAcceleration(enabled: Boolean) {
        if (!isAttachedToWindow || warningDialog != null) return
        if (enabled || !takeHardwareWarning()) {
            onHardwareAccelerationSelected(enabled)
            return
        }
        val message = SpannableString(context.getText(R.string.settings_hardware_warning)).apply {
            getSpans(0, length, Annotation::class.java).filter {
                it.key == "color" && it.value == "warning"
            }.forEach {
                setSpan(ForegroundColorSpan(0xFFE57373.toInt()), getSpanStart(it), getSpanEnd(it),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        warningDialog = ConfirmationDialog(context, R.drawable.ic_error_40, message,
            context.getString(R.string.settings_hardware_confirm),
            context.getString(R.string.settings_hardware_cancel)) {
            if (isAttachedToWindow) onHardwareAccelerationSelected(false)
        }.also { dialog ->
            dialog.setOnDismissListener { if (warningDialog === dialog) warningDialog = null }
            dialog.showWithin(rootView.width)
        }
    }

    override fun onDetachedFromWindow() {
        warningDialog?.dismiss()
        warningDialog = null
        super.onDetachedFromWindow()
    }

    private fun DisplayScaleOption.labelRes(): Int = when (this) {
        DisplayScaleOption.Small -> R.string.settings_display_scale_small
        DisplayScaleOption.Standard -> R.string.settings_display_scale_standard
        DisplayScaleOption.Large -> R.string.settings_display_scale_large
    }
}
