package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.networkSettings
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsPageScrollView
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.settings.components.ProxySettingsContent
import com.otokit.noharayh.ui.settings.components.ProxySettingsState

/** 三级表单的装配与生命周期；配置和网络执行仍归公开业务入口。 */
internal class ProxySettingsPage(context: Context, margin: Int, split: Boolean,
    val section: ProxySettingsSection, onBack: () -> Unit,
) : SettingsPageScrollView(context) {
    private val state = ProxySettingsState(networkSettings(context),
        context.getString(R.string.settings_proxy_save_failed), context.getString(R.string.settings_proxy_invalid_port))
    private val content = ProxySettingsContent(context, state, section)

    init {
        clipChildren = false
        clipToPadding = false
        isFillViewport = true
        addView(LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(if (split) 0 else margin, margin, margin, margin)
            if (split) addView(SettingsPageViews.action(context, context.getString(R.string.settings_archive_back),
                icon = SettingsButton.ActionIcon.Back, onClick = onBack), SettingsPageViews.gap(context))
            addView(content.view, SettingsPageViews.gap(context))
        }, FrameLayout.LayoutParams(-1, -2))
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) content.refresh()
    }

    override fun onDetachedFromWindow() {
        content.close()
        findFocus()?.clearFocus()
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        super.onDetachedFromWindow()
    }
}
