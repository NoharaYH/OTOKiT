package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import com.otokit.noharayh.ui.animation.settings.ThemePanelFrames
import com.otokit.noharayh.ui.animation.settings.ThemePanelTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.theme.ThemeOption
import kotlin.math.roundToInt

/** 标题命中区域保持固定高度，菜单、描边和外部布局共享展开进度。 */
@android.annotation.SuppressLint("ViewConstructor")
internal class ThemeSelectorPanel(
    context: Context,
    private val header: LabeledDropdownField,
    private val onHeightChanged: () -> Unit,
) : FrameLayout(context) {
    private val rowHeight = dp(Dimensions.FIELD_HEIGHT_DP)
    private var menu: ThemeDropdownMenu? = null
    private var menuOptions = emptyList<ThemeOption>()
    private var menuHeightBudget: Int? = null
    private var onOptionSelected: ((ThemeOption) -> Unit)? = null
    private var opening = false
    private var previewStart: Float? = null
    private var presentation = 1f
    private var controlsEnabled = true
    private val runner = ThemePanelTransitionRunner(::render)
    private val scrollbar = ThemeScrollbar(context,
        onDragStart = { menu?.beginScrollbarDrag() },
        onDrag = { menu?.dragScrollbarTo(it) },
        onDragEnd = { menu?.endScrollbarDrag() })
    val isOpen: Boolean get() = opening
    val extraHeight: Float get() = menu?.let {
        it.contentHeight * ThemePanelFrames.extension(runner.progress)
    } ?: 0f

    init {
        clipChildren = false
        clipToPadding = false
        addView(header, LayoutParams(-1, rowHeight))
        addView(scrollbar, LayoutParams(dp(Dimensions.SCROLLBAR_HIT_WIDTH_DP), -1,
            android.view.Gravity.RIGHT).apply {
            rightMargin = -dp(Dimensions.SCROLLBAR_HIT_WIDTH_DP) / 2 +
                (Dimensions.FIELD_STROKE_DP * resources.displayMetrics.density / 2f).roundToInt()
        })
    }

    fun open(options: List<ThemeOption>, availableHeight: Int, onSelected: (ThemeOption) -> Unit) {
        if (options.isEmpty()) { reset(); return }
        if (menu != null && menuOptions == options && menuHeightBudget == availableHeight) {
            // 相同内容保留当前展开/滚动状态，但事件接收者始终使用本次绑定。
            onOptionSelected = onSelected
            opening = true
            previewStart = null
            header.setExpanded(true)
            runner.play(1f)
            return
        }
        reset()
        opening = true
        menuOptions = options.toList()
        menuHeightBudget = availableHeight
        onOptionSelected = onSelected
        val content = ThemeDropdownMenu(context, options, availableHeight,
            onBottomRadiusChanged = { render(runner.progress, notifyHeight = false) },
            onScrollChanged = { renderScrollbar() }) { option ->
            if (opening && previewStart == null && controlsEnabled) {
                // 关闭动画被系统禁用时会同步 releaseMenu，先保留本次接收者。
                val selected = onOptionSelected
                close()
                selected?.invoke(option)
            }
        }
        menu = content
        addView(content, LayoutParams(-1, content.contentHeight).apply { topMargin = rowHeight })
        scrollbar.bringToFront()
        header.setExpanded(true)
        render(0f)
        runner.play(1f)
    }

    fun close() {
        if (menu == null || (!opening && previewStart == null)) return
        opening = false
        previewStart = null
        scrollbar.beginRetraction(runner.progress)
        header.setExpanded(false)
        menu?.stopMotion()
        runner.play(0f) { releaseMenu() }
    }

    fun beginBackPreview(): Boolean {
        if (!opening) return false
        previewStart = runner.progress
        scrollbar.beginRetraction(runner.progress)
        runner.preview(runner.progress)
        menu?.stopMotion()
        render(runner.progress)
        return true
    }
    fun previewBack(progress: Float) {
        val start = previewStart ?: return
        runner.preview(start * (1f - progress.coerceIn(0f, 1f)))
    }
    fun cancelBackPreview() {
        if (previewStart == null) return
        previewStart = null
        header.setExpanded(true)
        runner.play(1f)
    }
    fun finishBackPreview() { if (previewStart != null) close() }

    fun setPresentation(value: Float) {
        presentation = value
        header.alpha = value
        render(runner.progress, notifyHeight = false)
    }
    fun setControlsEnabled(enabled: Boolean) {
        controlsEnabled = enabled
        if (!enabled && previewStart == null) close()
        render(runner.progress, notifyHeight = false)
    }

    private fun render(progress: Float, notifyHeight: Boolean = true) {
        header.renderPanel(extraHeight, ThemePanelFrames.gap(progress),
            ThemePanelFrames.extension(progress), menu?.bottomCornerRadius ?: 0f)
        if (previewStart != null) header.previewExpanded(progress)
        menu?.let {
            val visibility = ThemePanelFrames.menu(progress)
            it.visibility = if (visibility > 0f) View.VISIBLE else View.INVISIBLE
            it.alpha = visibility * presentation
            it.translationY = -dp(Dimensions.MENU_TRANSLATION_DP) * (1f - visibility)
            val interactive = opening && previewStart == null && progress == 1f && controlsEnabled && presentation == 1f
            it.setInteractionEnabled(interactive)
            it.importantForAccessibility = if (interactive) IMPORTANT_FOR_ACCESSIBILITY_AUTO else IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        renderScrollbar()
        if (notifyHeight) onHeightChanged()
    }

    private fun renderScrollbar() {
        val (upper, lower) = header.scrollbarTrack()
        scrollbar.alpha = presentation
        scrollbar.render(upper, lower, if (lower > upper) 1f else 0f,
            menu?.scrollFraction ?: 0f, menu?.canScroll == true,
            menu != null && opening && previewStart == null && runner.progress == 1f && controlsEnabled && presentation == 1f,
            runner.progress, !opening || previewStart != null)
    }

    /** 从页面接收完整 48dp 热区；常规子 View 分发会把外框右侧的一半裁掉。 */
    fun dispatchScrollbarTouch(event: MotionEvent): Boolean {
        if (visibility != View.VISIBLE) return false
        val location = IntArray(2)
        scrollbar.getLocationOnScreen(location)
        val copy = MotionEvent.obtain(event)
        copy.offsetLocation(event.rawX - event.x - location[0], event.rawY - event.y - location[1])
        val handled = scrollbar.dispatchTouchEvent(copy)
        copy.recycle()
        return handled
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        renderScrollbar()
    }

    fun reset() {
        opening = false
        previewStart = null
        runner.stop()
        releaseMenu()
        runner.preview(0f)
        header.setExpanded(false)
    }
    private fun releaseMenu() {
        scrollbar.reset()
        menu?.stopMotion()
        menu?.let(::removeView)
        menu = null
        menuOptions = emptyList()
        menuHeightBudget = null
        onOptionSelected = null
        header.renderPanel(0f, 0f)
        onHeightChanged()
    }
    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
