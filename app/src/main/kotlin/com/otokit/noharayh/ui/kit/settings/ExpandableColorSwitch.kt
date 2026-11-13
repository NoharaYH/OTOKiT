package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.MotionEvent
import com.otokit.noharayh.ui.constants.dimensions.GroupedSwitchDimensions
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow

/** 同一块白底从开关头行生长到底部；只有头行空白处响应开关点击。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ExpandableColorSwitch(context: Context, label: CharSequence, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit) : GroupedSwitchRow(context, label, false, false,
    checked, onCheckedChange, labelColor = 0xFF323232.toInt()) {
    private val headerHeight = GroupedSwitchDimensions.ROW_HEIGHT_DP * resources.displayMetrics.density
    override val pressFeedbackHeight: Float get() = headerHeight

    init {
        clipChildren = false
        clipToPadding = false
    }

    fun renderExpansion(progress: Float) {
        bottomCornerPressFraction = 1f - progress
        invalidate()
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility") // 接受的手势由 PressSurface 统一 performClick。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y > headerHeight) return false
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
}
