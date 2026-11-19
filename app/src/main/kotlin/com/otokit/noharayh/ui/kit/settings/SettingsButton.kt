package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.configureSingleChoiceSemantics
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
    selected: Boolean = false, trailingIcon: ActionIcon = ActionIcon.Next,
    contentHeight: Int = ViewPrimitives.dp(context, Dimensions.HEIGHT_DP),
    private val choiceIndicatorVisible: Boolean = true,
    private val selectedTextColor: Int = Colors.SELECTED,
    onClick: () -> Unit,
) : PressSurface(context) {
    enum class ActionIcon { Next, ExternalLink, Back }
    enum class Kind { Action, Choice }
    private val labelView = ViewPrimitives.text(context, label, Dimensions.TEXT_SP, Colors.TEXT).apply {
        gravity = Gravity.CENTER_VERTICAL
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    private val indicator = FrameLayout(context)
    private val content = LinearLayout(context)
    private val transition = SettingsChoiceTransitionRunner()
    private var confirmed = selected
    private var contentColor: Int? = null
    fun textExtentFor(label: String, widthScale: Float): Float =
        dp(Dimensions.INSET_DP) + labelView.paint.measureText(label) * widthScale

    fun setContentColor(color: Int?) {
        contentColor = color
        val next = color ?: if (confirmed) selectedTextColor else Colors.TEXT
        if (labelView.currentTextColor != next) labelView.setTextColor(next)
    }
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)

    init {
        outerHaloEnabled = true
        fillColor = Colors.CONTENT
        contentDescription = label
        content.apply {
            gravity = Gravity.CENTER_VERTICAL
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            setPadding(dp(Dimensions.INSET_DP), 0, dp(Dimensions.INSET_DP), 0)
            addView(labelView, LinearLayout.LayoutParams(0, -1, 1f))
            if (kind == Kind.Choice) {
                if (choiceIndicatorVisible) addView(indicator,
                    LinearLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)))
            } else addView(ImageView(context).apply {
                setImageResource(when (trailingIcon) {
                    ActionIcon.Next -> R.drawable.ic_chevron_right_24
                    ActionIcon.ExternalLink -> R.drawable.ic_open_in_new_24
                    ActionIcon.Back -> R.drawable.ic_arrow_back_24
                })
                setColorFilter(Colors.MUTED)
            }, LinearLayout.LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)).apply { marginStart = dp(Dimensions.ICON_GAP_DP) })
        }
        addView(content, LayoutParams(-1, contentHeight))
        if (kind == Kind.Choice) configureSingleChoiceSemantics(selected)
        renderSelection(selected, animate = false)
        setOnClickListener { onClick() }
    }

    /** 菜单折叠时只隐藏图文，保留表面及其按压反馈。 */
    fun setContentAlpha(value: Float) { content.alpha = value.coerceIn(0f, 1f) }

    fun renderSelection(selected: Boolean, animate: Boolean = true) {
        confirmed = selected && kind == Kind.Choice
        isSelected = confirmed
        val radius = dp(if (confirmed) Dimensions.SELECTED_RADIUS_DP else Dimensions.RADIUS_DP).toFloat()
        val color = contentColor ?: if (confirmed) selectedTextColor else Colors.TEXT
        if (kind == Kind.Choice && choiceIndicatorVisible) {
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
