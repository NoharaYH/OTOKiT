package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import com.otokit.noharayh.ui.constants.dimensions.SettingsInfoDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives

/** 只读信息行；布局和配色由自身契约提供，不借用卡片或按钮的参数。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class SettingsInfoRow(context: Context, label: String, value: String) : LinearLayout(context) {
    init {
        gravity = Gravity.CENTER_VERTICAL
        val inset = ViewPrimitives.dp(context, Dimensions.INSET_DP)
        setPadding(inset, 0, inset, 0)
        minimumHeight = ViewPrimitives.dp(context, Dimensions.MIN_HEIGHT_DP)
        background = ViewPrimitives.shape(context, Colors.CONTENT, Dimensions.RADIUS_DP)
        addView(ViewPrimitives.text(context, label, Dimensions.TEXT_SP, Colors.TEXT).apply {
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1; ellipsize = TextUtils.TruncateAt.END
        }, LayoutParams(0, -1, 1f))
        addView(ViewPrimitives.text(context, value, Dimensions.TEXT_SP, Colors.TITLE, true).apply {
            gravity = Gravity.CENTER_VERTICAL
        }, LayoutParams(-2, -1))
    }
}
