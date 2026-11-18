package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.preferences.CapturePreferences
import com.otokit.noharayh.storage.preferences.BooleanPreferences

/** UI 装配与 Android 任务入口共用的构造接线，不保存任务状态。 */
internal fun capturePreferences(context: Context): CapturePreferences {
    val storage = BooleanPreferences(context, "capture_preferences")
    return CapturePreferences(storage::read, storage::write)
}
