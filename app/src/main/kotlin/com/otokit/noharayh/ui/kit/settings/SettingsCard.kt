package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.GroupedSurface
import com.otokit.noharayh.ui.kit.common.GroupPosition

/** 非交互复合容器，提供可选内容及尾部槽位；无内容时不留下间距。 */
internal class SettingsCard(context: Context, icon: Int, title: Int,
    content: View? = null, trailing: View? = null,
) : LinearLayout(context), GroupedSurface {
    private val shape = GradientDrawable().apply { setColor(Colors.SURFACE) }
    private var contentView: View? = null
    private val radii = FloatArray(8)
    private fun dp(value: Int) = SettingsPageViews.dp(context, value)

    init {
        orientation = VERTICAL
        clipChildren = false; clipToPadding = false
        background = shape
        val pad = dp(Dimensions.CONTENT_INSET_DP)
        setPadding(pad, pad, pad, pad)
        val header = LinearLayout(context).apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Dimensions.HEADER_HEIGHT_DP)
            clipChildren = false; clipToPadding = false
            addView(ImageView(context).apply { setImageResource(icon); setColorFilter(Colors.TEXT) },
                LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)))
            addView(SettingsPageViews.text(context, context.getString(title), Dimensions.TITLE_SP, Colors.TITLE, true).apply {
                letterSpacing = Dimensions.TITLE_LETTER_SPACING
            }, LayoutParams(-2, -2).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
            if (trailing != null) addView(trailing, LayoutParams(0, -2, 1f).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
        }
        addView(header, LayoutParams(-1, -2))
        setContent(content)
        setGroupPosition(GroupPosition.Single)
    }

    fun setContent(content: View?) {
        if (contentView === content) return
        contentView?.let(::removeView)
        contentView = content
        if (content != null) addView(content, LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.CONTENT_INSET_DP) })
    }

    override fun setGroupPosition(position: GroupPosition) {
        val outer = dp(Dimensions.CORNER_DP).toFloat()
        val inner = dp(Dimensions.CONTENT_CORNER_DP).toFloat()
        radii.fill(if (position.first) outer else inner, 0, 4)
        radii.fill(if (position.last) outer else inner, 4, 8)
        shape.cornerRadii = radii
    }
}
