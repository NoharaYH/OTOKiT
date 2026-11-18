package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.otokit.noharayh.R
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.preferences.CapturePreferences
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.common.dialog.ConfirmationDialog
import com.otokit.noharayh.ui.kit.settings.SettingsGroup

/** 页面装配连接偏好契约；组件只接收确认值和切换意图。 */
internal object ScoreSyncSettingsPage {
    fun create(context: Context, margin: Int, split: Boolean): ScrollView {
        val state = ScoreSyncSettingsState(capturePreferences(context))
        var infoDialog: ConfirmationDialog? = null
        lateinit var toggle: GroupedSwitchRow
        toggle = GroupedSwitchRow(context, context.getString(R.string.settings_capture_keep_callback),
            true, true, state.keepCallbackCaptureUntilLogin, { enabled ->
                state.selectKeepCallbackCapture(enabled)
                toggle.isChecked = state.keepCallbackCaptureUntilLogin
            }, infoDescription = context.getString(R.string.settings_capture_keep_callback_info), onInfoClick = {
                if (infoDialog == null) {
                    infoDialog = ConfirmationDialog(context, R.drawable.ic_info_outline_24,
                        context.getString(R.string.settings_capture_keep_callback_description),
                        context.getString(R.string.settings_info_dismiss), null,
                        accentColor = SettingsComponentColors.TEXT, onConfirmed = {}).also { dialog ->
                        dialog.setOnDismissListener { infoDialog = null }
                        dialog.showWithin(toggle.rootView.width)
                    }
                }
            })
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            setPadding(if (split) 0 else margin, margin, margin, margin)
            addView(SettingsGroup(context).apply { addView(toggle, LinearLayout.LayoutParams(-1, -2)) })
        }
        return object : ScrollView(context) {
            override fun onDetachedFromWindow() {
                infoDialog?.dismiss()
                infoDialog = null
                super.onDetachedFromWindow()
            }
        }.apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
    }
}

internal class ScoreSyncSettingsState(private val preferences: CapturePreferences) {
    var keepCallbackCaptureUntilLogin = preferences.keepCallbackCaptureUntilLogin()
        private set

    fun selectKeepCallbackCapture(enabled: Boolean) {
        preferences.setKeepCallbackCaptureUntilLogin(enabled)
        keepCallbackCaptureUntilLogin = preferences.keepCallbackCaptureUntilLogin()
    }
}
