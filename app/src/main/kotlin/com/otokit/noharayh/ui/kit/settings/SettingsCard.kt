package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.motion.SettingsGroupMotion
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
import com.otokit.noharayh.ui.animation.common.GroupShape

/** 非交互复合容器，提供可选内容及尾部槽位；无内容时不留下间距。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组件，需要显式内容与回调。
internal class SettingsCard(context: Context, icon: Int, title: Int,
    content: View? = null, trailing: View? = null,
) : LinearLayout(context), GroupedSurface {
    private val shape = GradientDrawable().apply { setColor(Colors.SURFACE) }
    private var contentView: View? = null
    private var animatedSlot: ExpandableContent? = null
    private var contentExpanded = true
    private val radii = FloatArray(8)
    private val groupShape = GroupShape(this,
        SettingsGroupDimensions.INNER_RADIUS_DP * resources.displayMetrics.density,
        SettingsGroupDimensions.OUTER_RADIUS_DP * resources.displayMetrics.density,
        SettingsGroupMotion.SHAPE,
    ) { top, bottom ->
        radii.fill(top, 0, 4); radii.fill(bottom, 4, 8); shape.cornerRadii = radii
    }
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)

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
            addView(ViewPrimitives.text(context, context.getString(title), Dimensions.TITLE_SP, Colors.TITLE, true).apply {
                letterSpacing = Dimensions.TITLE_LETTER_SPACING
            }, LayoutParams(-2, -2).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
            if (trailing != null) addView(trailing, LayoutParams(0, -2, 1f).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
        }
        addView(header, LayoutParams(-1, -2))
        setContent(content)
        renderGroupEdges(1f, 1f)
    }

    fun setContent(content: View?) {
        if (contentView === content) return
        animatedSlot?.let { slot ->
            slot.setContent(null)
            removeView(slot)
        }
        animatedSlot = null
        contentView?.let(::removeView)
        contentView = content
        if (content != null) addView(content, LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.CONTENT_INSET_DP) })
        if (!contentExpanded) setContentExpanded(false, false)
    }

    /** 动态内容由内部槽位负责高度、入退场及输入门控。 */
    fun setContentExpanded(expanded: Boolean, animate: Boolean = true) {
        contentExpanded = expanded
        val view = contentView ?: return
        val slot = animatedSlot ?: ExpandableContent(context).also { next ->
            removeView(view)
            next.setContent(view)
            next.onProgress = { progress ->
                (next.layoutParams as? LayoutParams)?.let { params ->
                    params.topMargin = (dp(Dimensions.CONTENT_INSET_DP) * progress).toInt()
                }
            }
            addView(next, LayoutParams(-1, -2).apply { topMargin = dp(Dimensions.CONTENT_INSET_DP) })
            animatedSlot = next
        }
        slot.setExpanded(expanded, animate)
    }

    fun replaceContent(content: View?, animate: Boolean = true) {
        if (contentView == null || content == null) { setContent(content); return }
        if (animatedSlot == null) setContentExpanded(contentExpanded, false)
        animatedSlot!!.replaceContent(content, animate)
        contentView = content
    }

    override fun setGroupPosition(position: GroupPosition) {
        groupShape.setEdges(if (position.first) 1f else 0f, if (position.last) 1f else 0f)
    }
    override fun renderGroupEdges(first: Float, last: Float) = groupShape.setEdges(first, last, false)

    override fun onDetachedFromWindow() {
        groupShape.finish()
        super.onDetachedFromWindow()
    }
}
