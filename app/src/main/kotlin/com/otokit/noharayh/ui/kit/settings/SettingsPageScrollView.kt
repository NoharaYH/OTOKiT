package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import com.otokit.noharayh.ui.constants.dimensions.SettingsPageDimensions
import com.otokit.noharayh.ui.kit.common.ContentScrollView
import com.otokit.noharayh.ui.kit.common.ViewPrimitives.dp

/** 设置二级内容使用完整绘制视口，首尾拉动只移动子内容。 */
internal open class SettingsPageScrollView(context: Context) :
    ContentScrollView(context, dp(context, SettingsPageDimensions.EDGE_OVERSHOOT_DP).toFloat())
