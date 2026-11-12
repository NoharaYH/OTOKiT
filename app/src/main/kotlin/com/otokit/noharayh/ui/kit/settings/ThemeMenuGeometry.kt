package com.otokit.noharayh.ui.kit.settings

import kotlin.math.roundToInt

/** 一步只交换一对行的高度；其余完整行、薄条和所有间距保持不变。 */
internal class ThemeMenuGeometry(
    val count: Int,
    val slots: Int,
    val rowHeight: Float,
    val gap: Float,
    val collapsedHeight: Float,
    val padding: Float,
) {
    val stride = rowHeight + gap
    val maximum = (count - slots).coerceAtLeast(0) * stride
    val height = slots * rowHeight + (count - slots) * collapsedHeight +
        (count - 1).coerceAtLeast(0) * gap + padding * 2

    fun snap(offset: Float): Float =
        ((offset / stride).roundToInt() * stride).coerceIn(0f, maximum)

    fun bounds(index: Int, offset: Float): Pair<Float, Float> {
        val progress = offset.coerceIn(0f, maximum) / stride
        val expandedBefore = (index - progress).coerceIn(0f, slots.toFloat())
        val top = padding + index * (collapsedHeight + gap) +
            expandedBefore * (rowHeight - collapsedHeight)
        return top to top + collapsedHeight + expansion(index, offset) * (rowHeight - collapsedHeight)
    }

    fun expansion(index: Int, offset: Float): Float {
        val progress = offset.coerceIn(0f, maximum) / stride
        return (minOf(index + 1f, progress + slots) - maxOf(index.toFloat(), progress))
            .coerceIn(0f, 1f)
    }
}

/** 以实际绘制的整像素高度判定展开完成，避免弹簧尾段在同一选项上重复触发。 */
internal class ThemeMenuExpansionFeedback(private val geometry: ThemeMenuGeometry) {
    private val armed = BooleanArray(geometry.count) { it >= geometry.slots }

    fun update(offset: Float): Boolean {
        var expanded = false
        for (index in 0 until geometry.count) {
            val fraction = geometry.expansion(index, offset)
            if (fraction <= 0.9f) armed[index] = true
            val height = geometry.collapsedHeight + fraction * (geometry.rowHeight - geometry.collapsedHeight)
            if (armed[index] && height.roundToInt() >= geometry.rowHeight.roundToInt()) {
                armed[index] = false
                expanded = true
            }
        }
        return expanded
    }
}
