package com.otokit.noharayh.ui.settings.subPages

import android.content.Context
import android.text.format.DateFormat
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.otokit.noharayh.R
import com.otokit.noharayh.auth.OAuthAuthorizationCheck
import com.otokit.noharayh.auth.OAuthFeedback
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.composition.exportPreferences
import com.otokit.noharayh.composition.oauthConnections
import com.otokit.noharayh.preferences.CapturePreferences
import com.otokit.noharayh.preferences.ExportPreferences
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors
import com.otokit.noharayh.ui.kit.common.InfoIconButton
import com.otokit.noharayh.ui.kit.settings.SettingsPageScrollView
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.dialog.AlternateExplanation
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsCheckButton
import com.otokit.noharayh.ui.kit.settings.SettingsCheckButton.State
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.kit.settings.ExpandableSwitchGroup
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderStatus
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import java.io.Closeable
import java.util.Date

/** 页面装配连接授权与采集偏好；组件只接收展示内容和操作意图。 */
internal object ScoreSyncSettingsPage {
    fun create(
        context: Context,
        margin: Int,
        split: Boolean,
        openLink: (String) -> Unit,
        openArchive: (ScoreArchiveKind) -> Unit,
        openLogs: () -> Unit = {},
        openProxy: (ProxySettingsSection) -> Unit = {},
    ): ScrollView {
        val state = ScoreSyncSettingsState(capturePreferences(context), exportPreferences(context))
        val oauth = oauthConnections(context)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
            clipToPadding = false
            val horizontal = if (split) 0 else margin
            setPadding(horizontal, margin, horizontal, margin)
        }
        var infoDialog: MessageDialog? = null
        fun showInfo(@StringRes technical: Int, @StringRes plain: Int) {
            if (infoDialog != null) return
            infoDialog = MessageDialog(context, R.drawable.ic_info_outline_24,
                context.getString(technical),
                context.getString(R.string.settings_info_dismiss), null,
                accentColor = SettingsComponentColors.TEXT,
                alternateExplanation = AlternateExplanation(
                    context.getString(plain),
                    context.getString(R.string.settings_info_plain),
                    context.getString(R.string.settings_info_technical)), onConfirmed = {}).also { dialog ->
                dialog.setOnDismissListener { infoDialog = null }
                dialog.showWithin(body.rootView.width)
            }
        }

        lateinit var toggle: GroupedSwitchRow
        toggle = GroupedSwitchRow(context, context.getString(R.string.settings_capture_keep_callback),
            true, true, state.keepCallbackCaptureUntilLogin, { enabled ->
                state.selectKeepCallbackCapture(enabled)
                toggle.isChecked = state.keepCallbackCaptureUntilLogin
            }, infoDescription = context.getString(R.string.settings_capture_keep_callback_info), onInfoClick = {
                showInfo(R.string.settings_capture_keep_callback_description, R.string.settings_capture_keep_callback_plain)
            })
        lateinit var redactToggle: GroupedSwitchRow
        redactToggle = GroupedSwitchRow(context, context.getString(R.string.settings_export_redact),
            true, true, state.redactExports, { enabled ->
                state.selectRedactExports(enabled)
                redactToggle.isChecked = state.redactExports
            }, infoDescription = context.getString(R.string.settings_export_redact_info), onInfoClick = {
                showInfo(R.string.settings_export_redact_description, R.string.settings_export_redact_plain)
            })
        lateinit var htmlToggle: GroupedSwitchRow
        htmlToggle = GroupedSwitchRow(context, context.getString(R.string.settings_export_logs_html),
            true, true, state.includeHtmlWithLogs, { enabled ->
                state.selectIncludeHtmlWithLogs(enabled)
                htmlToggle.isChecked = state.includeHtmlWithLogs
            }, infoDescription = context.getString(R.string.settings_export_logs_html_info), onInfoClick = {
                showInfo(R.string.settings_export_logs_html_technical, R.string.settings_export_logs_html_plain)
            })
        lateinit var logOptions: ExpandableSwitchGroup
        lateinit var logToggle: GroupedSwitchRow
        logToggle = GroupedSwitchRow(context, context.getString(R.string.settings_sync_logs_save),
            true, true, state.recordScoreSyncLogs, { enabled ->
                state.selectRecordScoreSyncLogs(enabled)
                logToggle.isChecked = state.recordScoreSyncLogs
                logOptions.setExpanded(state.recordScoreSyncLogs)
            }, infoDescription = context.getString(R.string.settings_sync_logs_save_info), onInfoClick = {
                showInfo(R.string.settings_sync_logs_save_technical, R.string.settings_sync_logs_save_plain)
            })
        val authorizationGroup = SettingsGroup(context)
        val noExpiry = context.getString(R.string.oauth_expiry_unknown)
        val dateFormat = DateFormat.getDateFormat(context)
        val timeFormat = DateFormat.getTimeFormat(context)
        val authorizationControls = OAuthProvider.entries.associateWith { provider ->
            val action = SettingsPageViews.action(
                context, context.getString(R.string.oauth_get_authorization),
                icon = SettingsButton.ActionIcon.ExternalLink, deferClick = true,
            ) {
                oauth.authorize(provider, { url ->
                    if (body.isAttachedToWindow && body.hasWindowFocus()) openLink(url)
                }, { result ->
                    if (body.isAttachedToWindow) showOAuthFeedback(context, provider, result)
                })
            }
            val check = SettingsCheckButton(context, context.getString(provider.checkLabel)) {
                oauth.checkAuthorization(provider)
            }
            val expiry = SettingsHeaderStatus(context)
            val content = SettingsGroup(context).apply {
                addView(action, LinearLayout.LayoutParams(-1, -2))
                addView(check, LinearLayout.LayoutParams(-1, -2))
            }
            authorizationGroup.addView(SettingsPageViews.card(
                context, R.drawable.ic_sync_24, provider.authorizationTitle, content = content, trailing = expiry,
            ), LinearLayout.LayoutParams(-1, -2))
            AuthorizationControls(check, expiry)
        }
        body.addView(authorizationGroup, LinearLayout.LayoutParams(-1, -2))
        body.addView(SettingsGroup(context).apply {
            for (kind in ScoreArchiveKind.entries) {
                val title = context.getString(kind.title.chinese)
                val action = SettingsPageViews.action(context, context.getString(R.string.settings_archive_view)) {
                    openArchive(kind)
                }.apply {
                    contentDescription = context.getString(R.string.settings_archive_view_named, title)
                }
                val info = InfoIconButton(context, context.getString(R.string.settings_archive_info, title)) {
                    when (kind) {
                        ScoreArchiveKind.Html -> showInfo(R.string.settings_archive_html_technical, R.string.settings_archive_html_plain)
                        ScoreArchiveKind.Json -> showInfo(R.string.settings_archive_json_technical, R.string.settings_archive_json_plain)
                    }
                }
                addView(SettingsCard(context, R.drawable.ic_archive_record_24, kind.title.chinese,
                    content = action, headerInfo = info), LinearLayout.LayoutParams(-1, -2))
            }
            val logContent = SettingsPageViews.action(context, context.getString(R.string.settings_archive_view),
                deferClick = true, onClick = openLogs).apply {
                contentDescription = context.getString(R.string.settings_archive_view_named,
                    context.getString(R.string.settings_sync_logs))
            }
            val logInfo = InfoIconButton(context, context.getString(R.string.settings_archive_info,
                context.getString(R.string.settings_sync_logs))) {
                showInfo(R.string.settings_sync_logs_technical, R.string.settings_sync_logs_plain)
            }
            addView(SettingsCard(context, R.drawable.ic_archive_record_24, R.string.settings_sync_logs,
                content = logContent, headerInfo = logInfo), LinearLayout.LayoutParams(-1, -2))
        }, SettingsPageViews.gap(context))
        body.addView(SettingsGroup(context).apply {
            addView(toggle, LinearLayout.LayoutParams(-1, -2))
            addView(redactToggle, LinearLayout.LayoutParams(-1, -2))
            logOptions = ExpandableSwitchGroup(context, logToggle, htmlToggle, state.recordScoreSyncLogs)
            addView(logOptions, LinearLayout.LayoutParams(-1, -2))
        }, SettingsPageViews.gap(context))
        val proxyEntries = SettingsGroup(context).apply {
            for (section in ProxySettingsSection.entries) {
                addView(SettingsPageViews.action(context, context.getString(section.titleRes), deferClick = true) {
                    openProxy(section)
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
        body.addView(SettingsCard(context, R.drawable.ic_route_24, R.string.settings_proxy,
            content = proxyEntries), SettingsPageViews.gap(context))

        return object : SettingsPageScrollView(context) {
            private var oauthSubscription: Closeable? = null

            override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
                super.onWindowFocusChanged(hasWindowFocus)
                if (hasWindowFocus) {
                    state.refreshRedactExports()
                    redactToggle.isChecked = state.redactExports
                    htmlToggle.isChecked = state.includeHtmlWithLogs
                    logToggle.isChecked = state.recordScoreSyncLogs
                    logOptions.setExpanded(state.recordScoreSyncLogs)
                }
            }

            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                oauthSubscription = oauth.observe {
                    for ((provider, controls) in authorizationControls) {
                        val result = oauth.authorizationCheck(provider)
                        val expiresAt = oauth.account(provider)?.refreshExpiresAt
                        val invalid = provider == OAuthProvider.LXNS && result == OAuthAuthorizationCheck.INVALID
                        val expired = expiresAt != null && expiresAt <= System.currentTimeMillis()
                        val expiry = when {
                            expiresAt == null -> noExpiry
                            invalid -> context.getString(R.string.oauth_expiry_invalid)
                            else -> {
                                val date = Date(expiresAt)
                                val timestamp = "${dateFormat.format(date)} ${timeFormat.format(date)}"
                                context.getString(R.string.oauth_expiry_at, timestamp)
                            }
                        }
                        val problem = expired || (invalid && expiresAt != null)
                        val expiryIcon = if (problem || expiresAt == null) {
                            R.drawable.ic_history_off_24
                        } else {
                            R.drawable.ic_history_2_24
                        }
                        controls.expiry.render(
                            expiry, expiryIcon,
                            if (problem) SettingsComponentColors.WARNING else SettingsComponentColors.TEXT,
                        )
                        val (state, label, icon) = checkPresentation(provider, result)
                        controls.check.render(state, context.getString(label), icon)
                    }
                }
            }

            override fun onDetachedFromWindow() {
                oauthSubscription?.close()
                oauthSubscription = null
                infoDialog?.dismiss()
                infoDialog = null
                super.onDetachedFromWindow()
            }
        }.apply {
            isFillViewport = true
            clipChildren = false
            clipToPadding = false
            if (split) setPadding(0, 0, margin, 0)
            addView(body, FrameLayout.LayoutParams(-1, -2))
        }
    }

    private data class AuthorizationControls(val check: SettingsCheckButton, val expiry: SettingsHeaderStatus)

    private data class CheckPresentation(val state: State, @StringRes val label: Int, @DrawableRes val icon: Int? = null)

    private fun checkPresentation(provider: OAuthProvider, result: OAuthAuthorizationCheck): CheckPresentation {
        val online = provider == OAuthProvider.LXNS
        return when (result) {
            OAuthAuthorizationCheck.UNCHECKED -> CheckPresentation(State.READY, provider.checkLabel)
            OAuthAuthorizationCheck.CHECKING -> CheckPresentation(State.CHECKING, R.string.oauth_checking)
            OAuthAuthorizationCheck.VALID -> CheckPresentation(State.SUCCESS,
                if (online) R.string.oauth_check_success else R.string.oauth_local_check_success)
            OAuthAuthorizationCheck.INVALID -> CheckPresentation(State.FAILURE,
                if (online) R.string.oauth_check_invalid else R.string.oauth_local_check_invalid)
            OAuthAuthorizationCheck.RATE_LIMITED -> CheckPresentation(State.WARNING,
                R.string.oauth_check_rate_limited, R.drawable.ic_pace_24)
            OAuthAuthorizationCheck.APPLICATION_REJECTED -> CheckPresentation(State.FAILURE,
                R.string.oauth_check_application_rejected, R.drawable.ic_brightness_alert_24)
            OAuthAuthorizationCheck.PERMISSION_DENIED -> CheckPresentation(State.FAILURE,
                R.string.oauth_check_permission_denied, R.drawable.ic_lock_reset_24)
            OAuthAuthorizationCheck.SERVICE_UNAVAILABLE -> CheckPresentation(State.WARNING,
                R.string.oauth_check_service_unavailable, R.drawable.ic_cloud_alert_24)
            OAuthAuthorizationCheck.UNAVAILABLE -> CheckPresentation(State.WARNING,
                if (online) R.string.oauth_check_unavailable else R.string.oauth_local_check_unavailable)
        }
    }
}

internal class ScoreSyncSettingsState(
    private val preferences: CapturePreferences,
    private val exports: ExportPreferences,
) {
    var keepCallbackCaptureUntilLogin = preferences.keepCallbackCaptureUntilLogin()
        private set
    var redactExports = exports.redactExports()
        private set
    var includeHtmlWithLogs = exports.includeHtmlWithLogs()
        private set
    var recordScoreSyncLogs = preferences.recordScoreSyncLogs()
        private set

    fun selectKeepCallbackCapture(enabled: Boolean) {
        preferences.setKeepCallbackCaptureUntilLogin(enabled)
        keepCallbackCaptureUntilLogin = preferences.keepCallbackCaptureUntilLogin()
    }

    fun selectRedactExports(enabled: Boolean) {
        exports.setRedactExports(enabled)
        refreshRedactExports()
    }

    fun refreshRedactExports() {
        redactExports = exports.redactExports()
        includeHtmlWithLogs = exports.includeHtmlWithLogs()
        recordScoreSyncLogs = preferences.recordScoreSyncLogs()
    }

    fun selectIncludeHtmlWithLogs(enabled: Boolean) {
        exports.setIncludeHtmlWithLogs(enabled)
        includeHtmlWithLogs = exports.includeHtmlWithLogs()
    }

    fun selectRecordScoreSyncLogs(enabled: Boolean) {
        preferences.setRecordScoreSyncLogs(enabled)
        recordScoreSyncLogs = preferences.recordScoreSyncLogs()
    }
}

internal fun showOAuthFeedback(context: Context, provider: OAuthProvider, feedback: OAuthFeedback) {
    val message = when (feedback) {
        OAuthFeedback.AUTHORIZED -> R.string.oauth_success
        OAuthFeedback.CANCELLED -> R.string.oauth_cancelled
        OAuthFeedback.INVALID_CALLBACK -> R.string.oauth_invalid_callback
        OAuthFeedback.NETWORK_ERROR -> R.string.oauth_network_error
        OAuthFeedback.REJECTED -> R.string.oauth_rejected
        OAuthFeedback.LOCAL_ERROR -> R.string.oauth_local_error
    }
    val providerName = context.getString(provider.title)
    Toast.makeText(context, context.getString(message, providerName), Toast.LENGTH_SHORT).show()
}

private val OAuthProvider.title
    get() = when (this) {
        OAuthProvider.DIVING_FISH -> R.string.oauth_provider_diving_fish
        OAuthProvider.LXNS -> R.string.oauth_provider_lxns
    }

private val OAuthProvider.authorizationTitle
    get() = when (this) {
        OAuthProvider.DIVING_FISH -> R.string.oauth_authorization_diving_fish
        OAuthProvider.LXNS -> R.string.oauth_authorization_lxns
    }

private val OAuthProvider.checkLabel
    get() = if (this == OAuthProvider.LXNS) R.string.oauth_check else R.string.oauth_local_check
