package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.constants.settings.SettingsPalette
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews

internal object ScoreSyncSettingsPage {
    fun create(context: Context, margin: Int, split: Boolean) = SettingsPageViews.placeholder(
        context, context.getString(R.string.settings_tile_sync), SettingsPalette.SYNC, margin, split,
    )
}
