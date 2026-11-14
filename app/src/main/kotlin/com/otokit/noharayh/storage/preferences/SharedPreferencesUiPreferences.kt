package com.otokit.noharayh.storage.preferences

import android.content.Context
import androidx.core.content.edit

/**
 * 基于平台 SharedPreferences 的最小实现。
 *
 * 使用 applicationContext，单次 Editor 更新相关字段并 apply；不持有 Activity，
 * 也不监听其他来源的偏好变化。
 */
class SharedPreferencesUiPreferences(context: Context) : UiPreferences {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun readSelection(): UiPreferenceRecord {
        val stored = preferences.all
        return UiPreferenceRecord(
            version = stored[KEY_VERSION] as? Int,
            currentGame = stored[KEY_CURRENT_GAME] as? String,
            maiThemeId = stored[KEY_THEME_MAI] as? String,
            chuThemeId = stored[KEY_THEME_CHU] as? String,
            themeMode = stored[KEY_THEME_MODE] as? String,
            mergedThemeId = stored[KEY_MERGED_THEME] as? String,
            backgroundHardwareAcceleration = stored[KEY_BACKGROUND_HARDWARE] as? Boolean,
            backgroundHtmlAnimations = stored[KEY_BACKGROUND_ANIMATIONS] as? Boolean,
            noneThemeSeedColor = stored[KEY_NONE_SEED] as? Int,
            noneThemeWallpaperSecondary = stored[KEY_NONE_WALLPAPER_SECONDARY] as? Int,
            noneThemeUseWallpaper = stored[KEY_NONE_WALLPAPER] as? Boolean,
            noneThemePrimaryColor = stored[KEY_NONE_PRIMARY] as? Int,
            noneThemeSecondaryColor = stored[KEY_NONE_SECONDARY] as? Int,
            noneThemeTintBackground = stored[KEY_NONE_BACKGROUND] as? Boolean,
        )
    }

    override fun writeSelection(
        version: Int,
        currentGame: String,
        maiThemeId: String,
        chuThemeId: String,
        themeMode: String,
        mergedThemeId: String,
        noneThemeSeedColor: Int,
        noneThemeTintBackground: Boolean,
        noneThemeUseWallpaper: Boolean,
        noneThemePrimaryColor: Int?,
        noneThemeSecondaryColor: Int?,
        noneThemeWallpaperSecondary: Int?,
    ) {
        preferences.edit {
            putInt(KEY_VERSION, version)
            putString(KEY_CURRENT_GAME, currentGame)
            putString(KEY_THEME_MAI, maiThemeId)
            putString(KEY_THEME_CHU, chuThemeId)
            putString(KEY_THEME_MODE, themeMode)
            putString(KEY_MERGED_THEME, mergedThemeId)
            putInt(KEY_NONE_SEED, noneThemeSeedColor)
            if (noneThemeWallpaperSecondary == null) remove(KEY_NONE_WALLPAPER_SECONDARY) else putInt(KEY_NONE_WALLPAPER_SECONDARY, noneThemeWallpaperSecondary)
            putBoolean(KEY_NONE_WALLPAPER, noneThemeUseWallpaper)
            if (noneThemePrimaryColor == null) remove(KEY_NONE_PRIMARY) else putInt(KEY_NONE_PRIMARY, noneThemePrimaryColor)
            if (noneThemeSecondaryColor == null) remove(KEY_NONE_SECONDARY) else putInt(KEY_NONE_SECONDARY, noneThemeSecondaryColor)
            putBoolean(KEY_NONE_BACKGROUND, noneThemeTintBackground)
        }
    }

    override fun writeBackgroundRendering(hardwareAccelerated: Boolean, htmlAnimations: Boolean) {
        preferences.edit {
            putBoolean(KEY_BACKGROUND_HARDWARE, hardwareAccelerated)
            putBoolean(KEY_BACKGROUND_ANIMATIONS, htmlAnimations)
        }
    }

    companion object {
        const val FILE_NAME: String = "ui_shell_preferences"
        const val KEY_VERSION: String = "version"
        const val KEY_CURRENT_GAME: String = "current_game"
        const val KEY_THEME_MAI: String = "theme_mai"
        const val KEY_THEME_CHU: String = "theme_chu"
        const val KEY_THEME_MODE: String = "theme_mode"
        const val KEY_MERGED_THEME: String = "merged_theme"
        const val KEY_BACKGROUND_HARDWARE = "background_hardware_acceleration"
        const val KEY_BACKGROUND_ANIMATIONS = "background_html_animations"
        const val KEY_NONE_WALLPAPER_SECONDARY = "none_theme_wallpaper_secondary"
        const val KEY_NONE_SEED = "none_theme_seed_color"
        const val KEY_NONE_WALLPAPER = "none_theme_use_wallpaper"
        const val KEY_NONE_PRIMARY = "none_theme_primary_color"
        const val KEY_NONE_SECONDARY = "none_theme_secondary_color"
        const val KEY_NONE_BACKGROUND = "none_theme_tint_background"
    }
}
