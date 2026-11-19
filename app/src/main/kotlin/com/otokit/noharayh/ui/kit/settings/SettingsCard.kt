package com.otokit.noharayh.ui.kit.settings

import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.constants.dimensions.SettingsGroupDimensions
import com.otokit.noharayh.ui.constants.motion.SettingsGroupMotion
import android.content.Context
import com.otokit.noharayh.ui.kit.common.ContinuousCornerDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions as Dimensions
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.GroupedSurface
import com.otokit.noharayh.ui.kit.common.GroupPosition
import com.otokit.noharayh.ui.kit.common.InfoIconButton
import com.otokit.noharayh.ui.animation.common.GroupShape
import com.otokit.noharayh.ui.animation.common.ContentTransition
import com.otokit.noharayh.ui.constants.motion.ContainerMotion

/** 非交互复合容器，提供可选内容及尾部槽位；无内容时不留下间距。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组件，需要显式内容与回调。
internal class SettingsCard(context: Context, icon: Int, title: Int,
    content: View? = null, trailing: View? = null,
    private val fillContent: Boolean = false,
    private val headerInfo: InfoIconButton? = null,
) : LinearLayout(context), GroupedSurface {
    private val shape = ContinuousCornerDrawable(Colors.SURFACE, 0f)
    private val inset = dp(Dimensions.CONTENT_INSET_DP)
    private val headerGap = dp(Dimensions.HEADER_CONTENT_GAP_DP)
    private val infoAnchor = headerInfo?.let { View(context).apply { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO } }
    private val header = LinearLayout(context)
    private val titleView = ViewPrimitives.text(context, context.getString(title), Dimensions.TITLE_SP, Colors.TITLE, true).apply {
        letterSpacing = Dimensions.TITLE_LETTER_SPACING
    }
    private var pendingTitle = titleView.text
    private var titleStartAlpha = 1f
    private var titleChanging = false
    private val titleTransition = ContentTransition(1f, ContainerMotion.CONTENT,
        { a, b, t -> a + (b - a) * t }, ::renderTitle)
    private var contentView: View? = null
    private var animatedSlot: ExpandableContent? = null
    private var contentExpanded = true
    private var groupTopRadius = 0f
    private var groupBottomRadius = 0f
    private var bottomCornerExtension = 0f
    private val groupShape = GroupShape(this,
        SettingsGroupDimensions.INNER_RADIUS_DP * resources.displayMetrics.density,
        SettingsGroupDimensions.OUTER_RADIUS_DP * resources.displayMetrics.density,
        SettingsGroupMotion.SHAPE,
    ) { top, bottom ->
        groupTopRadius = top
        groupBottomRadius = bottom
        renderCorners()
    }
    private fun dp(value: Int) = ViewPrimitives.dp(context, value)

    init {
        orientation = VERTICAL
        clipChildren = false; clipToPadding = false
        background = shape
        setPadding(inset, dp(Dimensions.HEADER_TOP_INSET_DP), inset, headerGap)
        header.apply {
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(Dimensions.HEADER_HEIGHT_DP)
            clipChildren = false; clipToPadding = false
            addView(ImageView(context).apply { setImageResource(icon); setColorFilter(Colors.TEXT) },
                LayoutParams(dp(Dimensions.ICON_DP), dp(Dimensions.ICON_DP)))
            addView(titleView, LayoutParams(-2, -2).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
            if (infoAnchor != null && headerInfo != null) addView(infoAnchor, LayoutParams(headerInfo.minimumWidth, 0))
            if (trailing != null) addView(trailing, LayoutParams(0, -2, 1f).apply { marginStart = dp(Dimensions.HEADER_GAP_DP) })
        }
        addView(header, LayoutParams(-1, -2))
        headerInfo?.let { button ->
            // Overlay the full hit target on the header without adding to the vertical layout.
            addView(button, LayoutParams(button.minimumWidth, button.minimumHeight).apply { bottomMargin = -height })
            button.translationZ = 1f
        }
        setContent(content)
        renderGroupEdges(1f, 1f)
    }

    fun setTitle(title: CharSequence) {
        if (title == pendingTitle) return
        pendingTitle = title
        titleStartAlpha = titleView.alpha
        titleChanging = titleView.text != title
        titleTransition.play(1f, animate = isAttachedToWindow, from = 0f)
    }

    private fun renderTitle(progress: Float) {
        val handoff = .4f
        titleView.alpha = when {
            !titleChanging -> titleStartAlpha + (1f - titleStartAlpha) * progress
            progress < handoff -> titleStartAlpha * (1f - progress / handoff)
            else -> {
                if (titleView.text != pendingTitle) titleView.text = pendingTitle
                (progress - handoff) / (1f - handoff)
            }
        }
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
        if (content != null) addView(content, LayoutParams(-1, if (fillContent) 0 else -2).apply {
            if (fillContent) weight = 1f
            spaceContent(1f)
        })
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
                (next.layoutParams as? LayoutParams)?.spaceContent(progress)
            }
            addView(next, LayoutParams(-1, -2).apply { spaceContent(1f) })
            animatedSlot = next
        }
        slot.setExpanded(expanded, animate)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        val button = headerInfo ?: return
        val anchor = checkNotNull(infoAnchor)
        val x = header.left + anchor.left
        val y = header.top + (header.height - button.measuredHeight) / 2
        button.layout(x, y, x + button.measuredWidth, y + button.measuredHeight)
    }

    private fun LayoutParams.spaceContent(progress: Float) {
        topMargin = (headerGap * progress).toInt()
        bottomMargin = ((inset - headerGap) * progress).toInt()
    }

    /** 布置由内容自身驱动高度的槽位，间距仍由卡片统一计算。 */
    fun setContentLayout(height: Int, progress: Float) {
        val view = contentView ?: return
        val params = view.layoutParams as LayoutParams
        val previousTop = params.topMargin
        val previousBottom = params.bottomMargin
        params.spaceContent(progress)
        if (params.height != height || params.topMargin != previousTop || params.bottomMargin != previousBottom) {
            params.height = height
            view.layoutParams = params
        }
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

    /** 内容驱动的底角增量（px），与分组底角合成；不另开动画或改变上角。 */
    fun renderBottomCornerExtension(radius: Float) {
        val next = radius.coerceAtLeast(0f)
        if (bottomCornerExtension == next) return
        bottomCornerExtension = next
        renderCorners()
    }

    private fun renderCorners() = shape.setRadii(groupTopRadius, groupBottomRadius + bottomCornerExtension)

    override fun onDetachedFromWindow() {
        titleTransition.finish()
        groupShape.finish()
        super.onDetachedFromWindow()
    }
}
