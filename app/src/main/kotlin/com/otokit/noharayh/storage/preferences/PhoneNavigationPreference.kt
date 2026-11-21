package com.otokit.noharayh.storage.preferences

import android.content.Context

/** 进程第一次创建主壳时确定样式；同进程的 Activity 重建沿用它。 */
internal class PhoneNavigationPreference(context: Context) {
    private val prefs = context.getSharedPreferences("phone_navigation", Context.MODE_PRIVATE)
    var selected: Boolean
        get() = prefs.getBoolean("floating", false)
        set(value) { prefs.edit().putBoolean("floating", value).apply() }

    val active = processStyle ?: selected.also { processStyle = it }

    private companion object { var processStyle: Boolean? = null }
}
