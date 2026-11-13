package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import androidx.core.graphics.withSave
import android.view.View
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.settings.ThemeCardFrame
import com.otokit.noharayh.ui.animation.settings.ThemeCardTransitionRunner
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions
import com.otokit.noharayh.ui.constants.dimensions.SettingsCardDimensions
import com.otokit.noharayh.ui.constants.motion.ThemeSelectionMotion
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import com.otokit.noharayh.ui.kit.settings.LabeledDropdownField
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.dp
import com.otokit.noharayh.ui.kit.settings.SettingsSegmentedChoice
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.constants.dimensions.GroupedSwitchDimensions
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.NoneThemePalette
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import com.otokit.noharayh.ui.kit.settings.NoneThemeColorPicker
import com.otokit.noharayh.ui.kit.settings.ExpandableColorSwitch
import com.otokit.noharayh.ui.animation.settings.ColorPickerTransitionRunner
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
    onBackgroundTintSelected: (Boolean) -> Unit,
    onWallpaperSelected: (Boolean) -> Unit,
    onColorSelected: (NoneThemeColorRole, Int) -> Unit,
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
    private val switchHeight = dp(context, GroupedSwitchDimensions.ROW_HEIGHT_DP)
    private val backgroundSwitch = GroupedSwitchRow(context,
        context.getString(R.string.settings_none_theme_tint_background), false, true,
        selection.noneThemeTintBackground, onBackgroundTintSelected,
        labelColor = 0xFF323232.toInt())
    private val wallpaperSwitch = ExpandableColorSwitch(context,
        context.getString(R.string.settings_none_theme_wallpaper), selection.noneThemeUseWallpaper, onWallpaperSelected)
    private val picker = NoneThemeColorPicker(context, onColorSelected)
    private var pickerProgress = if (selection.noneThemeUseWallpaper) 0f else 1f
    private val pickerMotion = ColorPickerTransitionRunner()
    private val pickerHost = FrameLayout(context).apply {
        clipToPadding = false
        addView(picker, FrameLayout.LayoutParams(-1, picker.panelHeight))
    }
    private val wallpaperBlock = wallpaperSwitch.apply {
        addView(pickerHost, FrameLayout.LayoutParams(-1, 0).apply { topMargin = switchHeight })
    }
    private val noneOptions = FrameLayout(context).apply {
        clipChildren = false
        clipToPadding = false
        addView(wallpaperBlock, FrameLayout.LayoutParams(-1, switchHeight))
        addView(backgroundSwitch, FrameLayout.LayoutParams(-1, switchHeight))
    }
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
    private val firstPanel = ThemeSelectorPanel(context, firstField, ::layoutContent)
    private val secondPanel = ThemeSelectorPanel(context, secondField, ::layoutContent)
    private val panels = listOf(firstPanel, secondPanel)
    private var backPanel: ThemeSelectorPanel? = null
    private var scrollbarTouchPanel: ThemeSelectorPanel? = null
    private val themeCard = card(context, R.drawable.ic_format_paint_24,
        R.string.settings_theme_selection, content, trailing = segment)
    private val themeCardShape = themeCard.background as GradientDrawable
    private val themeCardRadii = FloatArray(8)

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
            togglePanel(firstPanel, options.filterNot { it.id == id }) { onOptionSelected(it, game) }
        }
        secondField.setOnClickListener {
            val state = renderedSelection ?: return@setOnClickListener
            togglePanel(secondPanel, ThemeOptions.forGame(GameLine.Chu)
                .filterNot { it.id == state.chuThemeId }) { onOptionSelected(it, GameLine.Chu) }
        }
        content.addView(firstPanel, FrameLayout.LayoutParams(-1, rowHeight))
        content.addView(secondPanel, FrameLayout.LayoutParams(-1, rowHeight).apply {
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
        val themeGroup = PressFrontColumn(context).apply {
            addView(themeCard, LinearLayout.LayoutParams(-1, -2))
            addView(noneOptions, LinearLayout.LayoutParams(-1, 0))
        }
        themeGroup.bind(backgroundSwitch)
        body.addView(themeGroup, LinearLayout.LayoutParams(-1, -2))
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
        backgroundSwitch.isChecked = selection.noneThemeTintBackground
        wallpaperSwitch.isChecked = selection.noneThemeUseWallpaper
        picker.renderColors(NoneThemePalette.resolve(selection.noneThemeSeedColor, false,
            selection.noneThemePrimaryColor, selection.noneThemeSecondaryColor).colors)
        if (previous == null || previous.noneThemeUseWallpaper != selection.noneThemeUseWallpaper) {
            picker.resetSelection()
            val target = if (selection.noneThemeUseWallpaper) 0f else 1f
            if (previous != null && isAttachedToWindow) pickerMotion.play(pickerProgress, target) {
                pickerProgress = it
                layoutNoneOptions()
                updateControls()
            } else { pickerProgress = target; layoutNoneOptions() }
        }
        if (previous?.themeMode != selection.themeMode) picker.resetSelection()
        segment.setSelection(modeIndex(selection.themeMode))
        val title = firstTitle(selection)
        if (previous?.themeMode == selection.themeMode) {
            if (!transition.isRunning) {
                firstField.setValue(title)
                secondField.setValue(ThemeOptions.resolveForGame(GameLine.Chu, selection.chuThemeId).title)
            }
            return
        }
        panels.forEach { it.close() }
        val morph = previous != null && previous.themeMode != ThemeSelectionMode.None &&
            selection.themeMode != ThemeSelectionMode.None
        val valueChanged = when {
            // 无主题没有待展示的新值，保留原文字随整个选择框退场。
            selection.themeMode == ThemeSelectionMode.None && previous != null -> false
            morph -> {
                firstField.setValue(firstTitle(requireNotNull(previous)))
                firstField.prepareValueTransition(title)
            }
            else -> { firstField.setValue(title); false }
        }
        secondField.setValue(ThemeOptions.resolveForGame(GameLine.Chu, selection.chuThemeId).title)
        val target = when (selection.themeMode) {
            ThemeSelectionMode.Independent -> ThemeCardFrame((rowHeight * 2 + rowGap).toFloat(), 1f, 1f, 1f)
            ThemeSelectionMode.Merged -> ThemeCardFrame(rowHeight.toFloat(), 1f, 0f, 0f)
            ThemeSelectionMode.None -> ThemeCardFrame(prefix = transition.frame.prefix, none = 1f)
        }
        transition.play(target, animate = previous != null && isAttachedToWindow, valueChanged = valueChanged)
    }

    private fun firstTitle(state: GameSelectionState): String =
        if (state.themeMode == ThemeSelectionMode.Merged) ThemeOptions.resolveMerged(state.mergedThemeId).title
        else ThemeOptions.resolveForGame(GameLine.Mai, state.maiThemeId).title

    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        // 页面比外框宽：优先转交滑块的完整热区，其余位置仍走原来的菜单/页面手势。
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scrollbarTouchPanel = panels.firstOrNull { it.dispatchScrollbarTouch(event) }
            if (scrollbarTouchPanel != null) return true
        } else scrollbarTouchPanel?.let { panel ->
            panel.dispatchScrollbarTouch(event)
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                scrollbarTouchPanel = null
            }
            return true
        }
        return super.dispatchTouchEvent(event)
    }

    private fun renderFrame(frame: ThemeCardFrame) {
        layoutContent()
        // 过渡期间只裁内容；静止时释放裁剪，保留 PressSurface 外环。
        content.clipTransition = transition.isRunning
        fields.forEachIndexed { index, field ->
            val visibility = if (index == 0) frame.first else frame.second
            val panel = panels[index]
            panel.visibility = if (visibility > 0f) View.VISIBLE else View.INVISIBLE
            panel.setPresentation(visibility)
            panel.translationY = -ThemeSelectionMotion.FIELD_TRANSLATION_DP *
                resources.displayMetrics.density * (1f - visibility)
            field.importantForAccessibility = if (visibility == 1f && !transition.isRunning)
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        }
        firstField.setLabelPrefixProgress(frame.prefix)
        firstField.renderValueTransition(frame.value)
        noneOptions.visibility = if (frame.none > 0f) View.VISIBLE else View.GONE
        noneOptions.alpha = frame.none
        noneOptions.translationY = -rowGap * (1f - frame.none)
        layoutNoneOptions()
        // 开关进入分组时，只收小相邻的两边圆角；最外侧始终保留大圆角。
        val outerRadius = dp(context, GroupedSwitchDimensions.OUTER_RADIUS_DP).toFloat()
        val innerRadius = GroupedSwitchDimensions.INNER_RADIUS_DP * resources.displayMetrics.density
        val joinedRadius = outerRadius + (innerRadius - outerRadius) * frame.none
        themeCardRadii.fill(outerRadius, 0, 4)
        themeCardRadii.fill(joinedRadius, 4, 8)
        themeCardShape.cornerRadii = themeCardRadii
        backgroundSwitch.topCornerRadius = joinedRadius
        // 父层的透明度/位移不会重录子 View 的绘制，圆角变化需显式刷新底形。
        backgroundSwitch.invalidate()
        noneOptions.importantForAccessibility = if (frame.none == 1f && !transition.isRunning)
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        updateControls()
    }

    private fun layoutNoneOptions() {
        val pickerHeight = (picker.panelHeight * pickerProgress).roundToInt()
        wallpaperSwitch.renderExpansion(pickerProgress)
        pickerHost.visibility = if (pickerProgress > 0f) View.VISIBLE else View.GONE
        pickerHost.clipChildren = pickerProgress < 1f
        picker.alpha = pickerProgress
        picker.translationY = -rowGap * (1f - pickerProgress)
        pickerHost.importantForAccessibility = if (pickerProgress == 1f)
            View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        (pickerHost.layoutParams as FrameLayout.LayoutParams).let {
            if (it.height != pickerHeight) pickerHost.layoutParams = it.apply { height = pickerHeight }
        }
        val blockHeight = switchHeight + pickerHeight
        (wallpaperBlock.layoutParams as FrameLayout.LayoutParams).let {
            if (it.height != blockHeight) wallpaperBlock.layoutParams = it.apply { height = blockHeight }
        }
        val gap = dp(context, GroupedSwitchDimensions.GAP_DP)
        (backgroundSwitch.layoutParams as FrameLayout.LayoutParams).let {
            if (it.topMargin != blockHeight + gap) backgroundSwitch.layoutParams = it.apply { topMargin = blockHeight + gap }
        }
        val noneHeight = ((blockHeight + gap + switchHeight) * transition.frame.none).roundToInt()
        val noneMargin = (gap * transition.frame.none).roundToInt()
        (noneOptions.layoutParams as? LinearLayout.LayoutParams)?.let {
            if (it.height != noneHeight || it.topMargin != noneMargin) {
                noneOptions.layoutParams = it.apply { height = noneHeight; topMargin = noneMargin }
            }
        }
    }

    private fun layoutContent() {
        val frame = transition.frame
        val firstExtra = firstPanel.extraHeight * frame.first
        val secondExtra = secondPanel.extraHeight * frame.second
        val height = (frame.height + firstExtra + secondExtra).roundToInt()
        val gap = (dp(context, SettingsCardDimensions.CONTENT_INSET_DP) * frame.first).roundToInt()
        (content.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            if (params.height != height || params.topMargin != gap) {
                content.layoutParams = params.apply { this.height = height; topMargin = gap }
            }
        }
        panels.forEachIndexed { index, panel ->
            val params = panel.layoutParams as? FrameLayout.LayoutParams ?: return@forEachIndexed
            val panelHeight = rowHeight + panel.extraHeight.roundToInt()
            val top = if (index == 0) 0 else rowHeight + rowGap + firstExtra.roundToInt()
            if (params.height != panelHeight || params.topMargin != top) {
                panel.layoutParams = params.apply { this.height = panelHeight; topMargin = top }
            }
        }
    }

    private fun togglePanel(panel: ThemeSelectorPanel, options: List<ThemeOption>, onSelected: (ThemeOption) -> Unit) {
        if (panel.isOpen) { panel.close(); return }
        panels.filterNot { it === panel }.forEach { it.close() }
        // 预算只依赖视口，不随本次展开后的卡片高度变化，避免测量反馈循环。
        val budget = (height - rowHeight * 2 - rowGap * 4).coerceAtLeast(rowHeight + rowGap * 2)
        panel.open(options, budget, onSelected)
    }

    fun closeThemePanel(): Boolean {
        val panel = panels.firstOrNull { it.isOpen } ?: return false
        panel.close()
        return true
    }
    fun beginThemePanelBack(): Boolean {
        val panel = panels.firstOrNull { it.isOpen } ?: return false
        if (!panel.beginBackPreview()) return false
        backPanel = panel
        updateControls()
        return true
    }
    fun previewThemePanelBack(progress: Float) { backPanel?.previewBack(progress) }
    fun cancelThemePanelBack() {
        backPanel?.cancelBackPreview()
        backPanel = null
        updateControls()
    }
    fun finishThemePanelBack() {
        backPanel?.finishBackPreview()
        backPanel = null
        updateControls()
    }

    fun setControlsEnabled(enabled: Boolean) { controlsEnabled = enabled; updateControls() }

    private fun updateControls() {
        val enabled = controlsEnabled && !transition.isRunning
        segment.setControlsEnabled(enabled && backPanel == null)
        panels.forEach { it.setControlsEnabled(enabled) }
        firstField.setControlsEnabled(enabled && backPanel == null && transition.frame.first == 1f)
        secondField.setControlsEnabled(enabled && backPanel == null && transition.frame.second == 1f)
        val noneEnabled = enabled && backPanel == null && transition.frame.none == 1f
        backgroundSwitch.isEnabled = noneEnabled
        wallpaperSwitch.isEnabled = noneEnabled
        picker.setControlsEnabled(noneEnabled && pickerProgress == 1f)
    }

    fun setDisabledProgress(progress: Float) {
        segment.setDisabledProgress(progress)
        fields.forEach { it.setDisabledProgress(progress) }
    }

    override fun onDetachedFromWindow() {
        scrollbarTouchPanel = null
        backPanel = null
        pickerMotion.stop()
        pickerProgress = if (renderedSelection?.noneThemeUseWallpaper != false) 0f else 1f
        transition.finish()
        panels.forEach { it.reset() }
        super.onDetachedFromWindow()
    }

    private fun modeIndex(mode: ThemeSelectionMode): Int = when (mode) {
        ThemeSelectionMode.Independent -> 0
        ThemeSelectionMode.Merged -> 1
        ThemeSelectionMode.None -> 2
    }
}
