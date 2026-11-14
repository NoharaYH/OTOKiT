package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.widget.FrameLayout
import com.otokit.noharayh.ui.kit.settings.ThemeSettingsContent
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.NoneThemeColorRole

/** 页面只提供业务确认值及意图，标准组件负责布局与效果组合。 */
@android.annotation.SuppressLint("ViewConstructor") // 纯代码组件，需要显式内容与回调。
internal class PersonalizationSettingsPage(
    context: Context, margin: Int, split: Boolean, selection: GameSelectionState,
    onModeSelected: (ThemeSelectionMode) -> Unit,
    onOptionSelected: (ThemeOption, GameLine?) -> Unit,
    onBackgroundTintSelected: (Boolean) -> Unit,
    onWallpaperSelected: (Boolean) -> Unit,
    onColorSelected: (NoneThemeColorRole, Int) -> Unit,
) : FrameLayout(context) {
    private val content = ThemeSettingsContent(context, margin, split, selection,
        onModeSelected, onOptionSelected, onBackgroundTintSelected, onWallpaperSelected, onColorSelected)
    init { clipChildren = false; clipToPadding = false; addView(content, LayoutParams(-1, -1)) }
    fun renderSelection(selection: GameSelectionState) = content.renderSelection(selection)
    fun closeThemePanel() = content.closeThemePanel()
    fun beginThemePanelBack() = content.beginThemePanelBack()
    fun previewThemePanelBack(progress: Float) = content.previewThemePanelBack(progress)
    fun cancelThemePanelBack() = content.cancelThemePanelBack()
    fun finishThemePanelBack() = content.finishThemePanelBack()
    fun setControlsEnabled(enabled: Boolean) = content.setControlsEnabled(enabled)
    fun setDisabledProgress(progress: Float) = content.setDisabledProgress(progress)
}
