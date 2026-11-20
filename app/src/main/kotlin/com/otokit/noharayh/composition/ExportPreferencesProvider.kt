package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.preferences.ExportPreferences
import com.otokit.noharayh.storage.preferences.BooleanPreferences

internal fun exportPreferences(context: Context): ExportPreferences {
    val storage = BooleanPreferences(context, "export_preferences")
    return ExportPreferences(storage::read, storage::write)
}
