package com.otokit.noharayh.ui.kit.common

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout

/**
 * 纵向选项容器：解决相邻行按压外环的遮挡问题。
 *
 * 布局顺序始终按 addView 顺序；只把当前按压行提升为最后绘制，
 * 让它的外环盖在相邻行之上，而不是被下方的兄弟行裁掉/盖住。
 * 组件不决定选项内容、选中形状或颜色，只负责行排布和绘制层级。
 *
 * **宿主约束：** 本容器自身允许子 View 外溢；如果外层还有会裁剪的父容器，
 * 仍需要外层设置 `clipChildren=false`、`clipToPadding=false`。
 */
open class PressFrontColumn @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    private var front: View? = null
    private val bindings = mutableMapOf<PressSurface, () -> Unit>()

    init {
        orientation = VERTICAL
        clipChildren = false
        clipToPadding = false
        isChildrenDrawingOrderEnabled = true
    }

    override fun onViewAdded(child: View) {
        super.onViewAdded(child)
        if (child is PressSurface) bind(child)
    }

    override fun onViewRemoved(child: View) {
        super.onViewRemoved(child)
        // 包装行移除时也注销其内部按钮；不能只清理直接子 PressSurface。
        bindings.keys.filter { surface ->
            var current: View? = surface
            while (current != null && current !== child) current = current.parent as? View
            current === child
        }.forEach { bindings.remove(it)?.invoke() }
        if (front === child) {
            front = null
            invalidate()
        }
    }

    /**
     * 也用于包装行内部的按钮，按压时提升其所属的直接子行；重复绑定只生效一次。
     * 移除整行自动解绑；只移除内部按钮时，调用返回的注销函数。
     */
    fun bind(surface: PressSurface): () -> Unit {
        if (!bindings.containsKey(surface)) {
            bindings[surface] = surface.addOnPressStateChangedListener { pressed ->
                if (pressed) bringVisualToFront(surface)
            }
        }
        val binding = bindings.getValue(surface)
        return { if (bindings[surface] === binding) bindings.remove(surface)?.invoke(); Unit }
    }

    /** 接受直接子行或其后代；不改变布局顺序，不处理其他容器的 View。 */
    fun bringVisualToFront(child: View) {
        var row = child
        while (row.parent !== this) row = row.parent as? View ?: return
        if (front === row) return
        front = row
        invalidate()
    }

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        val frontIndex = front?.let(::indexOfChild) ?: -1
        if (frontIndex < 0) return drawingPosition
        if (drawingPosition == childCount - 1) return frontIndex
        return if (drawingPosition >= frontIndex) drawingPosition + 1 else drawingPosition
    }
}
