package com.otokit.noharayh.ui.settings.subPages

import com.otokit.noharayh.ui.kit.settings.SettingsButton.ActionIcon
import android.content.Context
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.FrameLayout
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.card
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.info
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.action
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews.gap

internal object AboutSettingsPage {
    fun create(context: Context, margin: Int, split: Boolean, openLink: (String) -> Unit): android.view.View {
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(if (split) 0 else margin, margin, margin, margin)
        }
        body.addView(card(context, R.drawable.ic_info_outline_24, R.string.about_card_version,
            info(context, context.getString(R.string.about_current_version), versionLabel(context))))
        body.addView(card(context, R.drawable.ic_update_24, R.string.about_card_update,
            action(context, context.getString(R.string.about_check_update)) { openLink("https://github.com/NoharaYH/OTOKiT/releases") }), gap(context))
        body.addView(card(context, R.drawable.ic_code_24, R.string.about_card_repo,
            action(context, context.getString(R.string.about_repo_url), icon = ActionIcon.ExternalLink, deferClick = true) { openLink("https://github.com/NoharaYH/OTOKiT") }), gap(context))
        body.addView(card(context, R.drawable.ic_feedback_24, R.string.about_card_feedback,
            action(context, context.getString(R.string.about_feedback_subtitle), icon = ActionIcon.ExternalLink, deferClick = true) { openLink("https://github.com/NoharaYH/OTOKiT/issues") }), gap(context))

        return ScrollView(context).apply {
            isFillViewport = true
            clipToPadding = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
    }
    private fun versionLabel(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "v${info.versionName} (+${androidx.core.content.pm.PackageInfoCompat.getLongVersionCode(info)})"
    }
}
