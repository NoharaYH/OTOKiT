package com.otokit.noharayh.ui.settings

import com.otokit.noharayh.ui.constants.motion.PageMotionStyle
import android.content.Context
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption
import com.otokit.noharayh.ui.settings.subPages.*
import com.otokit.noharayh.ui.shell.navigation.SettingsSection
import com.otokit.noharayh.ui.shell.selection.GameSelectionState
import com.otokit.noharayh.ui.theme.NoneThemeColorRole
import com.otokit.noharayh.ui.theme.GameLine
import com.otokit.noharayh.ui.theme.ThemeOption
import com.otokit.noharayh.ui.theme.ThemeSelectionMode
import com.otokit.noharayh.ui.theme.ThemeColors
import com.otokit.noharayh.ui.shell.background.BackgroundRenderingOptions

internal object SettingsPages {
    fun child(
        context: Context,
        section: SettingsSection,
        margin: Int,
        split: Boolean,
        openLink: (String) -> Unit,
        openArchive: (ScoreArchiveKind) -> Unit,
        displayScale: DisplayScaleOption,
        onDisplayScaleSelected: (DisplayScaleOption) -> Unit,
        rendering: BackgroundRenderingOptions,
        pageMotion: PageMotionStyle,
        onPageMotionSelected: (PageMotionStyle) -> Unit,
        startupAnimationEnabled: Boolean,
        onStartupAnimationSelected: (Boolean) -> Unit,
        onHardwareAccelerationSelected: (Boolean) -> Unit,
        onHtmlAnimationsSelected: (Boolean) -> Unit,
        takeHardwareWarning: () -> Boolean,
        selection: GameSelectionState,
        materkitColors: ThemeColors,
        onThemeModeSelected: (ThemeSelectionMode) -> Unit,
        onThemeOptionSelected: (ThemeOption, GameLine?) -> Unit,
        onNoneThemeBackgroundSelected: (Boolean) -> Unit,
        onNoneThemeWallpaperSelected: (Boolean) -> Unit,
        onNoneThemeColorSelected: (NoneThemeColorRole, Int) -> Unit,
        openLogs: () -> Unit = {},
        openProxy: (ProxySettingsSection) -> Unit = {},
    ) = when (section) {
        SettingsSection.ScoreSync -> ScoreSyncSettingsPage.create(context, margin, split, openLink, openArchive, openLogs, openProxy)
        SettingsSection.App -> AppSettingsPage(
            context = context,
            margin = margin,
            split = split,
            displayScale = displayScale,
            onDisplayScaleSelected = onDisplayScaleSelected,
            rendering = rendering,
            pageMotion = pageMotion,
            onPageMotionSelected = onPageMotionSelected,
            startupAnimationEnabled = startupAnimationEnabled,
            onStartupAnimationSelected = onStartupAnimationSelected,
            onHardwareAccelerationSelected = onHardwareAccelerationSelected,
            onHtmlAnimationsSelected = onHtmlAnimationsSelected,
            takeHardwareWarning = takeHardwareWarning,
        )
        SettingsSection.Personalization -> PersonalizationSettingsPage(
            context = context,
            margin = margin,
            split = split,
            selection = selection,
            materkitColors = materkitColors,
            onModeSelected = onThemeModeSelected,
            onOptionSelected = onThemeOptionSelected,
            onBackgroundTintSelected = onNoneThemeBackgroundSelected,
            onWallpaperSelected = onNoneThemeWallpaperSelected,
            onColorSelected = onNoneThemeColorSelected,
        )
        SettingsSection.About -> AboutSettingsPage.create(context, margin, split, openLink)
    }
}
