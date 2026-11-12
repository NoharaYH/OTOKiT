package com.otokit.noharayh.ui.kit.settings

import android.content.Context
import android.view.View
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
    private val gap = dp(Dimensions.MENU_ANCHOR_GAP_DP)
    private var menu: ThemeDropdownMenu? = null
    private var optionIds = emptyList<String>()
    private var opening = false
    private var previewStart: Float? = null
    private var presentation = 1f
    private var controlsEnabled = true
    private val runner = ThemePanelTransitionRunner(::render)
    val isOpen: Boolean get() = opening
    val extraHeight: Float get() = menu?.let {
        (it.contentHeight + 2 * gap) * ThemePanelFrames.extension(runner.progress)
    } ?: 0f

    init {
        clipChildren = false
        clipToPadding = false
        addView(header, LayoutParams(-1, rowHeight))
    }

    fun open(options: List<ThemeOption>, availableHeight: Int, onSelected: (ThemeOption) -> Unit) {
        if (options.isEmpty()) return
        if (menu != null && optionIds == options.map { it.id }) {
            opening = true
            previewStart = null
            header.setExpanded(true)
            runner.play(1f)
            return
        }
        reset()
        opening = true
        optionIds = options.map { it.id }
        val content = ThemeDropdownMenu(context, options, availableHeight) { option ->
            if (opening && previewStart == null && controlsEnabled) {
                close()
                onSelected(option)
            }
        }
        menu = content
        addView(content, LayoutParams(-1, content.contentHeight).apply { topMargin = rowHeight + gap })
        header.setExpanded(true)
        render(0f)
        runner.play(1f) { menu?.scheduleOpeningHaptic(header.openingHapticDelay()) }
    }

    fun close() {
        if (menu == null || (!opening && previewStart == null)) return
        opening = false
        previewStart = null
        header.setExpanded(false)
        menu?.stopMotion()
        runner.play(0f) { releaseMenu() }
    }

    fun beginBackPreview(): Boolean {
        if (!opening) return false
        previewStart = runner.progress
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
        header.renderPanel(extraHeight, ThemePanelFrames.gap(progress))
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
        if (notifyHeight) onHeightChanged()
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
        menu?.stopMotion()
        menu?.let(::removeView)
        menu = null
        optionIds = emptyList()
        header.renderPanel(0f, 0f)
        onHeightChanged()
    }
    override fun onDetachedFromWindow() {
        reset()
        super.onDetachedFromWindow()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
}
