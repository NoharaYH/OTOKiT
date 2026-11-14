package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.settings.SettingsChoiceTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.SettingsButtonDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.PressSurface

/** 成品操作/单选按钮；只发意图，由 renderSelection 接收确认状态。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组件，需要显式内容与回调。
internal class SettingsButton(context: Context, label: String, private val kind: Kind,
    selected: Boolean = false, external: Boolean = false, onClick: () -> Unit,
) : PressSurface(context) {
    enum class Kind { Action, Choice }
    private val labelView = SettingsPageViews.text(context, label, Dimensions.TEXT_SP, Colors.TEXT).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val indicator = FrameLayout(context)
    private val transition = SettingsChoiceTransitionRunner()
    private var confirmed = selected
    private fun dp(value: Int) = SettingsPageViews.dp(context, value)

    init {
        outerHaloEnabled = true
        deferClickUntilReleaseFeedback = external
        fillColor = Colors.CONTENT
        contentDescription = label
        val row = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setPadding(dp(Dimensions.INSET_DP), 0, dp(Dimensions.INSET_DP), 0)
            addView(labelView, LinearLayout.LayoutParams(0, -1, 1f))
            if (kind == Kind.Choice) addView(indicator, LinearLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)))
            else addView(ImageView(context).apply {
                setImageResource(if (external) R.drawable.ic_open_in_new_24 else R.drawable.ic_chevron_right_24)
                setColorFilter(Colors.MUTED)
            }, LinearLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)).apply { marginStart = dp(Dimensions.ICON_GAP_DP) })
        }
        addView(row, LayoutParams(-1, dp(Dimensions.HEIGHT_DP)))
        if (kind == Kind.Choice) configureSingleChoiceSemantics(selected)
        renderSelection(selected, animate = false)
        setOnClickListener { onClick() }
    }

    fun renderSelection(selected: Boolean, animate: Boolean = true) {
        confirmed = selected && kind == Kind.Choice
        isSelected = confirmed
        val radius = dp(if (confirmed) Dimensions.SELECTED_RADIUS_DP else Dimensions.RADIUS_DP).toFloat()
        val color = if (confirmed) Colors.SELECTED else Colors.TEXT
        if (kind == Kind.Choice) {
            indicator.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(Dimensions.RADIO_STROKE_DP), if (confirmed) Colors.SELECTED else Colors.MUTED)
            }
            indicator.removeAllViews()
            if (confirmed) indicator.addView(View(context).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Colors.SELECTED) }
            }, LayoutParams(dp(Dimensions.RADIO_DOT_DP), dp(Dimensions.RADIO_DOT_DP), Gravity.CENTER))
        }
        if (animate && isAttachedToWindow) transition.play(0, baseRadius, radius, labelView.currentTextColor, color) { r, c ->
            baseRadius = r; labelView.setTextColor(c)
        } else {
            transition.stop(); baseRadius = radius; labelView.setTextColor(color)
        }
    }

    override fun onDetachedFromWindow() {
        renderSelection(confirmed, animate = false)
        super.onDetachedFromWindow()
    }
}
