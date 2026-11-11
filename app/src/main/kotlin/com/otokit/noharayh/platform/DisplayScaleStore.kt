package com.otokit.noharayh.platform

import android.content.Context
import androidx.core.content.edit
import com.otokit.noharayh.ui.constants.display.DisplayScaleOption

/**
 * 应用内显示比例的本地存储。
 *
 * 这是平台配置，不属于游戏/主题选择；放在 platform 里，供 Activity 在
 * attachBaseContext 之前读取，不依赖 shell 或业务状态。
 */
class DisplayScaleStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): DisplayScaleOption =
        DisplayScaleOption.fromSavedValue(preferences.getString(KEY_SCALE, null))
            ?: DisplayScaleOption.DEFAULT

    fun write(option: DisplayScaleOption) {
        preferences.edit { putString(KEY_SCALE, option.savedValue) }
    }

    companion object {
        private const val FILE_NAME = "ui_display_scale"
        private const val KEY_SCALE = "scale"
    }
}
