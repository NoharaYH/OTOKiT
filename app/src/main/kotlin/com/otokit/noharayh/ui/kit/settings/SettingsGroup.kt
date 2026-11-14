package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import android.content.Context
import android.view.View
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.kit.common.GroupedSurface
import com.otokit.noharayh.ui.kit.common.GroupPosition
import com.otokit.noharayh.ui.kit.common.PressFrontColumn

/** 同一设置组的标准宿主：可见成员、间距、相邻圆角与按压绘制顺序。 */
internal class SettingsGroup(context: Context) : PressFrontColumn(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val members = (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }
        members.forEachIndexed { index, child ->
            val position = when {
                members.size == 1 -> GroupPosition.Single
                index == 0 -> GroupPosition.First
                index == members.lastIndex -> GroupPosition.Last
                else -> GroupPosition.Middle
            }
            (child as? GroupedSurface)?.setGroupPosition(position)
            (child.layoutParams as? LayoutParams)?.let {
                it.topMargin = if (index == 0) 0 else ViewPrimitives.dp(context, SettingsGroupDimensions.GAP_DP)
            }
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
