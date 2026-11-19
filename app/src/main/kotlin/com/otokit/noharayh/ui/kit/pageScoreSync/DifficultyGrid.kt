package com.otokit.noharayh.ui.kit.pageScoreSync

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import com.otokit.noharayh.ui.kit.common.PressSurface
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import kotlin.math.roundToInt

/** 仅排列谱面按钮并呈现多选状态。两款游戏分别提供独立的按钮。 */
internal class DifficultyGrid(
    context: Context,
    private val mai: Boolean,
    private val buttons: List<PressSurface>,
    onToggle: (Int) -> Unit,
) : ViewGroup(context) {
    private var columns = 2
    private var aspect = 3.125f
    private var gap = ViewPrimitives.dp(context, 12)
    private var cellHeight = 0
    private var glyphSizePx = 0

    init {
        setWide(false)
        clipChildren = false
        clipToPadding = false
        buttons.forEachIndexed { index, button ->
            addView(button)
            button.isFocusable = true
            button.setOnClickListener { onToggle(index) }
            button.accessibilityDelegate = object : AccessibilityDelegate() {
                override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
                    super.onInitializeAccessibilityNodeInfo(host, info)
                    info.className = "android.widget.CheckBox"
                    info.isCheckable = true
                    info.isChecked = host.isSelected
                }
            }
        }
    }

    fun render(selected: Set<Int>, interactive: Boolean) {
        buttons.forEachIndexed { index, button ->
            button.isSelected = index in selected
            button.isEnabled = interactive
        }
    }

    fun setWide(wide: Boolean) {
        val compactMai = mai && !wide
        columns = if (compactMai) 3 else 2
        aspect = if (compactMai) 2.2f else 3.125f
        gap = ViewPrimitives.dp(context, if (compactMai) 8 else 12)
        requestLayout()
    }

    fun setGlyphSize(px: Int) {
        if (glyphSizePx == px) return
        glyphSizePx = px
        for (button in buttons) {
            when (button) {
                is MaiDifficultyButton -> button.setArtworkHeight(px)
                is ChuDifficultyButton -> button.setLabelSize(px)
            }
        }
    }

    private fun cellLeft(column: Int, available: Int) =
        column * gap + column * (available - (columns - 1) * gap) / columns

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        cellHeight = (((width - gap * (columns - 1)) / columns) / aspect).roundToInt().coerceAtLeast(1)
        buttons.forEachIndexed { index, child ->
            val column = index % columns
            val cellWidth = (cellLeft(column + 1, width) - cellLeft(column, width) - gap).coerceAtLeast(1)
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(cellHeight, MeasureSpec.EXACTLY))
        }
        val rows = (buttons.size + columns - 1) / columns
        setMeasuredDimension(width, rows * cellHeight + (rows - 1) * gap)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        buttons.forEachIndexed { index, child ->
            val x = cellLeft(index % columns, width)
            val y = index / columns * (cellHeight + gap)
            child.layout(x, y, x + child.measuredWidth, y + cellHeight)
        }
    }
}
