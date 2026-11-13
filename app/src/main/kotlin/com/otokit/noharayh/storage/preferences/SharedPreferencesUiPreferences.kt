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
        )
    }

    override fun writeSelection(
        version: Int,
        currentGame: String,
        maiThemeId: String,
        chuThemeId: String,
        themeMode: String,
        mergedThemeId: String,
    ) {
        preferences.edit {
            putInt(KEY_VERSION, version)
            putString(KEY_CURRENT_GAME, currentGame)
            putString(KEY_THEME_MAI, maiThemeId)
            putString(KEY_THEME_CHU, chuThemeId)
            putString(KEY_THEME_MODE, themeMode)
            putString(KEY_MERGED_THEME, mergedThemeId)
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
    }
}
