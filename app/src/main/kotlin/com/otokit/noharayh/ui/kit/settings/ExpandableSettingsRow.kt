package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.MotionEvent
import android.view.View
import com.otokit.noharayh.ui.constants.dimensions.GroupedSwitchDimensions
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow

/** 可复用组合容器：标题行响应开关意图，内容槽位独立管理展开和输入。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组件，需要显式内容与回调。
internal class ExpandableSettingsRow(context: Context, label: CharSequence, checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) : GroupedSwitchRow(context, label, false, false, checked, onCheckedChange, labelColor = 0xFF323232.toInt()) {
    private var headerHeight = (GroupedSwitchDimensions.ROW_HEIGHT_DP * resources.displayMetrics.density).toInt()
    private val contentSlot = ExpandableContent(context)
    private var hasContent = false
    override val pressFeedbackHeight: Float get() = headerHeight.toFloat()
    var onExpansionChanged: ((Float) -> Unit)? = null

    init {
        clipChildren = false; clipToPadding = false
        contentSlot.onProgress = {
            bottomCornerPressFraction = if (hasContent) 1f - it else 1f
            onExpansionChanged?.invoke(it)
        }
        addView(contentSlot, LayoutParams(-1, -2).apply { topMargin = headerHeight })
    }
    fun setContent(content: View?) {
        hasContent = content != null
        contentSlot.setContent(content)
    }
    fun setExpanded(expanded: Boolean, animate: Boolean = true) = contentSlot.setExpanded(expanded, animate)
    fun setContentEnabled(enabled: Boolean) { contentSlot.controlsEnabled = enabled }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val header = getChildAt(0).measuredHeight
        if (header != headerHeight) {
            headerHeight = header
            (contentSlot.layoutParams as LayoutParams).topMargin = header
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }
    @android.annotation.SuppressLint("ClickableViewAccessibility") // PressSurface 接受的点击统一调用 performClick。
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y > headerHeight) return false
        return super.onTouchEvent(event)
    }
    override fun performClick(): Boolean = super.performClick()
}
