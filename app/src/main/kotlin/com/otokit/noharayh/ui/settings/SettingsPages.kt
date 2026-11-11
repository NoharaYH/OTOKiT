package com.otokit.noharayh.ui.settings

import android.content.Context
import com.otokit.noharayh.ui.settings.subPages.*
import com.otokit.noharayh.ui.shell.navigation.SettingsSection

internal object SettingsPages {
    fun child(context: Context, section: SettingsSection, margin: Int, split: Boolean, openLink: (String) -> Unit) = when (section) {
        SettingsSection.ScoreSync -> ScoreSyncSettingsPage.create(context, margin, split)
        SettingsSection.App -> AppSettingsPage.create(context, margin, split)
        SettingsSection.Personalization -> PersonalizationSettingsPage.create(context, margin, split)
        SettingsSection.About -> AboutSettingsPage.create(context, margin, split, openLink)
    }
}
