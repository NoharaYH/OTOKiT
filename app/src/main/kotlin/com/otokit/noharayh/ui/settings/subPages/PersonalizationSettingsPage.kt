package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.graphics.Canvas
import androidx.core.graphics.withSave
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.settings.ThemeCardFrame
import com.otokit.noharayh.ui.animation.settings.ThemeCardTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.dp
import com.otokit.noharayh.ui.kit.settings.SettingsSegmentedChoice
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeOptions
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import kotlin.math.roundToInt

@android.annotation.SuppressLint("ViewConstructor") // 页面装配必须提供选择快照和事件回调。
internal class PersonalizationSettingsPage(
    context: Context,
    margin: Int,
    split: Boolean,
    selection: GameSelectionState,
    private val onModeSelected: (ThemeSelectionMode) -> Unit,
    private val onOptionSelected: (ThemeOption, GameLine?) -> Unit,
) : ScrollView(context) {
    private var controlsEnabled = true
    private var renderedSelection: GameSelectionState? = null
    private val segment = SettingsSegmentedChoice(context)
    private val firstField = LabeledDropdownField(context,
        context.getString(R.string.settings_theme_merged_field))
    private val secondField = LabeledDropdownField(context,
        context.getString(R.string.settings_theme_chu))
    private val fields = listOf(firstField, secondField)
    private val rowHeight = dp(context, ThemeSelectionDimensions.FIELD_HEIGHT_DP)
    private val rowGap = dp(context, ThemeSelectionDimensions.FIELD_GAP_DP)
    private val content = object : FrameLayout(context) {
        var clipTransition = false
        init { clipChildren = false; clipToPadding = false }
        override fun dispatchDraw(canvas: Canvas) {
            canvas.withSave {
                // 浮动标题可伸出顶部；退场内容只按正在伸缩的底边裁剪。
                if (clipTransition) {
                    // 硬件 Canvas 的初始 clipBounds 可能只是本容器矩形，不能拿它的
                    // top=0 作裁剪上界；第一行的浮动标题还会随退场动画向上移动。
                    val overflow = maxOf(firstField.drawingOverflow, secondField.drawingOverflow)
                    val shift = ThemeSelectionMotion.FIELD_TRANSLATION_DP * resources.displayMetrics.density
                    clipRect(-overflow, -overflow - shift, width + overflow, height.toFloat())
                }
                super.dispatchDraw(this)
            }
        }
    }
    private val transition = ThemeCardTransitionRunner(::renderFrame)

    init {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
            clipChildren = false
            clipToPadding = false
        }
        firstField.configureLabelPrefix(context.getString(R.string.settings_theme_mai)
            .removeSuffix(context.getString(R.string.settings_theme_merged_field)))
        firstField.setOnClickListener {
            val state = renderedSelection ?: return@setOnClickListener
            val game = if (state.themeMode == ThemeSelectionMode.Merged) null else GameLine.Mai
            val id = if (game == null) state.mergedThemeId else state.maiThemeId
            val options = if (game == null) ThemeOptions.merged() else ThemeOptions.forGame(game)
            firstField.toggleMenu(options.filterNot { it.id == id }) { onOptionSelected(it, game) }
        }
        secondField.setOnClickListener {
            val state = renderedSelection ?: return@setOnClickListener
            secondField.toggleMenu(ThemeOptions.forGame(GameLine.Chu)
                .filterNot { it.id == state.chuThemeId }) { onOptionSelected(it, GameLine.Chu) }
        }
        content.addView(firstField, FrameLayout.LayoutParams(-1, rowHeight))
        content.addView(secondField, FrameLayout.LayoutParams(-1, rowHeight).apply {
            topMargin = rowHeight + rowGap
        })
        segment.setItems(listOf(
            context.getString(R.string.settings_theme_independent),
            context.getString(R.string.settings_theme_merged),
            context.getString(R.string.settings_theme_none),
        ), modeIndex(selection.themeMode)) { index ->
            onModeSelected(when (index) {
                0 -> ThemeSelectionMode.Independent
                1 -> ThemeSelectionMode.Merged
                else -> ThemeSelectionMode.None
            })
        }
        body.addView(card(context, R.drawable.ic_format_paint_24,
            R.string.settings_theme_selection, content, trailing = segment))
        isFillViewport = true
        clipChildren = false
        clipToPadding = false
        addView(body, FrameLayout.LayoutParams(-1, -2))
        renderSelection(selection)
    }

    /** 只消费确认快照；保留行实例以连续改变高度、标签缺口和可见度。 */
    fun renderSelection(selection: GameSelectionState) {
        val previous = renderedSelection
        if (previous == selection) return
        renderedSelection = selection
        segment.setSelection(modeIndex(selection.themeMode))
        val title = firstTitle(selection)
        if (previous?.themeMode == selection.themeMode) {
            if (!transition.isRunning) {
                firstField.setValue(title)
                secondField.setValue(ThemeOptions.resolveForGame(GameLine.Chu, selection.chuThemeId).title)
            }
            return
        }
        fields.forEach { it.dismissMenu() }
        val morph = previous != null && previous.themeMode != ThemeSelectionMode.None &&
            selection.themeMode != ThemeSelectionMode.None
        val valueChanged = if (morph) {
            firstField.setValue(firstTitle(requireNotNull(previous)))
            firstField.prepareValueTransition(title)
        } else { firstField.setValue(title); false }
        secondField.setValue(ThemeOptions.resolveForGame(GameLine.Chu, selection.chuThemeId).title)
        val target = when (selection.themeMode) {
            ThemeSelectionMode.Independent -> ThemeCardFrame((rowHeight * 2 + rowGap).toFloat(), 1f, 1f, 1f)
            ThemeSelectionMode.Merged -> ThemeCardFrame(rowHeight.toFloat(), 1f, 0f, 0f)
            ThemeSelectionMode.None -> ThemeCardFrame(prefix = transition.frame.prefix)
        }
        transition.play(target, animate = previous != null && isAttachedToWindow, valueChanged = valueChanged)
    }

    private fun firstTitle(state: GameSelectionState): String =
        if (state.themeMode == ThemeSelectionMode.Merged) ThemeOptions.resolveMerged(state.mergedThemeId).title
        else ThemeOptions.resolveForGame(GameLine.Mai, state.maiThemeId).title

    private fun renderFrame(frame: ThemeCardFrame) {
        val height = frame.height.roundToInt()
        if (content.layoutParams.height != height) {
            content.layoutParams = content.layoutParams.apply { this.height = height }
        }
        // 过渡期间只裁内容；静止时释放裁剪，保留 PressSurface 外环。
        content.clipTransition = transition.isRunning
        fields.forEachIndexed { index, field ->
            val visibility = if (index == 0) frame.first else frame.second
            field.visibility = if (visibility > 0f) View.VISIBLE else View.INVISIBLE
            field.alpha = visibility
            field.translationY = -ThemeSelectionMotion.FIELD_TRANSLATION_DP *
                resources.displayMetrics.density * (1f - visibility)
            field.importantForAccessibility = if (visibility == 1f && !transition.isRunning)
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        firstField.setLabelPrefixProgress(frame.prefix)
        firstField.renderValueTransition(frame.value)
        updateControls()
    }

    fun setControlsEnabled(enabled: Boolean) { controlsEnabled = enabled; updateControls() }

    private fun updateControls() {
        val enabled = controlsEnabled && !transition.isRunning
        segment.setControlsEnabled(enabled)
        firstField.setControlsEnabled(enabled && transition.frame.first == 1f)
        secondField.setControlsEnabled(enabled && transition.frame.second == 1f)
    }

    fun setDisabledProgress(progress: Float) {
        segment.setDisabledProgress(progress)
        fields.forEach { it.setDisabledProgress(progress) }
    }

    override fun onDetachedFromWindow() {
        transition.finish()
        super.onDetachedFromWindow()
    }

    private fun modeIndex(mode: ThemeSelectionMode): Int = when (mode) {
        ThemeSelectionMode.Independent -> 0
        ThemeSelectionMode.Merged -> 1
        ThemeSelectionMode.None -> 2
    }
}
