package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.View
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.kit.common.GroupPosition
import com.otokit.noharayh.ui.kit.common.GroupedSurface
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import kotlin.math.roundToInt

/** 关联开关共享展开帧：内容、间距和相邻圆角由同一进度驱动。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ExpandableSwitchGroup(
    context: Context,
    private val header: GroupedSwitchRow,
    private val content: GroupedSwitchRow,
    expanded: Boolean,
) : PressFrontColumn(context), GroupedSurface {
    private val slot = ExpandableContent(context)
    private val gap = ViewPrimitives.dp(context, SettingsGroupDimensions.GAP_DP)
    private var first = 1f
    private var last = 1f
    private var expandedTarget = expanded
    private var unbindParent = emptyList<() -> Unit>()

    init {
        slot.setContent(content)
        addView(header, LayoutParams(-1, -2))
        addView(slot, LayoutParams(-1, -2))
        bind(content)
        slot.onProgress = { progress ->
            content.visibility = if (progress == 0f) View.GONE else View.VISIBLE
            (slot.layoutParams as LayoutParams).topMargin = (gap * progress).roundToInt()
            renderEdges()
        }
        slot.setExpanded(expanded, false)
    }

    fun setExpanded(expanded: Boolean, animate: Boolean = true) {
        if (expandedTarget == expanded) return
        expandedTarget = expanded
        slot.setExpanded(expanded, animate)
    }

    override fun setGroupPosition(position: GroupPosition) = renderGroupEdges(
        if (position.first) 1f else 0f, if (position.last) 1f else 0f)

    override fun renderGroupEdges(first: Float, last: Float) {
        this.first = first
        this.last = last
        renderEdges()
    }

    private fun renderEdges() {
        header.renderGroupEdges(first, last * (1f - slot.progress))
        content.renderGroupEdges(1f - slot.progress, last)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        (parent as? PressFrontColumn)?.let { unbindParent = listOf(it.bind(header), it.bind(content)) }
    }

    override fun onDetachedFromWindow() {
        unbindParent.forEach { it() }
        unbindParent = emptyList()
        super.onDetachedFromWindow()
    }
}
