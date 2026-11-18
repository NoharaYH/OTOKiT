package com.otokit.noharayh.storage.preferences

import android.content.Context
import androidx.core.content.edit

/** 原始布尔值存取；字段名称与缺省规则由调用方决定。 */
internal class BooleanPreferences(context: Context, fileName: String) {
    private val values = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)
    fun read(key: String): Boolean? = values.all[key] as? Boolean
    fun write(key: String, value: Boolean) { values.edit { putBoolean(key, value) } }
}
