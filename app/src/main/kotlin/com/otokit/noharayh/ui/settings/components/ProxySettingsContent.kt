package com.otokit.noharayh.ui.settings.components

import android.content.Context
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.network.*
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.PressFrontColumn
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsSection
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsCheckButton
import com.otokit.noharayh.ui.kit.settings.SettingsCompactButton
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderActions
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderStatus
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.kit.settings.SettingsTextField
import java.io.Closeable

/** 六个三级页的业务组合；草稿保留在当前页，确认后才交给配置入口。 */
internal class ProxySettingsContent(private val context: Context, private val state: ProxySettingsState,
    private val section: ProxySettingsSection,
) : Closeable {
    private val clearFormFeedback = mutableListOf<() -> Unit>()
    val view: View = when (section) {
        ProxySettingsSection.UPSTREAM -> editUpstream()
        ProxySettingsSection.PORTS -> editPorts()
        ProxySettingsSection.LISTEN -> editListen()
        ProxySettingsSection.CHECK -> checkChannel()
        ProxySettingsSection.RESTORE -> restore()
        ProxySettingsSection.URLS -> chooseUrl()
    }

    fun refresh() = state.refresh()

    private fun column() = PressFrontColumn(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun LinearLayout.add(view: View) = addView(view, SettingsPageViews.gap(context).apply {
        // 首项由外层容器提供留白，避免说明、表单和首个输入重复叠加间距。
        if (childCount == 0) topMargin = 0
    })

    private fun field(label: Int, value: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                      hint: String? = null) = SettingsTextField(context, context.getString(label), value, type, hint)

    private fun editUpstream(): View {
        state.refresh()
        val current = state.snapshot.upstream
        val host = field(R.string.settings_proxy_host, current?.host.orEmpty())
        val port = field(R.string.settings_proxy_port, current?.port?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER)
        val username = field(R.string.settings_proxy_username, current?.username.orEmpty())
        val password = field(R.string.settings_proxy_password, current?.password.orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val scopes = state.snapshot.scopes.toMutableSet()
        val body = column().apply {
            add(host); add(port); add(username); add(password)
        }
        val options = ProxyScope.entries.map { scope ->
            lateinit var row: GroupedSwitchRow
            row = GroupedSwitchRow(context, context.getString(scopeLabel(scope)), true, true, scope in scopes, { checked ->
                if (checked) scopes.add(scope) else scopes.remove(scope)
                row.isChecked = checked
                clearFormStatus()
            })
            row
        }
        return form(R.string.settings_proxy_upstream_message, body, groupedOptions = options, separateSave = true) {
            state.saveUpstream(host.text, port.text, username.text, password.text, scopes)
        }
    }

    private fun editPorts(): View {
        state.refresh()
        val overrides = state.overrides
        val defaults = NetworkSnapshot.defaults()
        val entry = field(R.string.settings_proxy_entry_port, overrides.entryPort?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER,
            context.getString(R.string.settings_proxy_default_value, defaults.entryPort.toString()))
        val proxy = field(R.string.settings_proxy_listen_port, overrides.proxyPort?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER,
            context.getString(R.string.settings_proxy_default_value, defaults.proxyPort.toString()))
        return form(R.string.settings_proxy_ports_message, column().apply { add(entry); add(proxy) }, separateSave = true) {
            state.savePorts(entry.text, proxy.text)
        }
    }

    private fun editListen(): View {
        state.refresh()
        val values = state.overrides
        val defaults = NetworkSnapshot.defaults()
        val entry = field(R.string.settings_proxy_entry_host, values.entryHost.orEmpty(),
            hint = context.getString(R.string.settings_proxy_default_value, defaults.entryHost))
        val proxy = field(R.string.settings_proxy_listen_host, values.proxyHost.orEmpty(),
            hint = context.getString(R.string.settings_proxy_default_value, defaults.proxyHost))
        return form(R.string.settings_proxy_listen_message, column().apply { add(entry); add(proxy) }, separateSave = true) {
            state.saveListen(entry.text, proxy.text)
        }
    }

    private fun restore(): View {
        val selected = linkedSetOf<NetworkSection>()
        val body = SettingsGroup(context)
        val rows = linkedMapOf<NetworkSection, GroupedSwitchRow>()
        lateinit var all: GroupedSwitchRow
        all = GroupedSwitchRow(context, context.getString(R.string.settings_proxy_restore_all), true, true, false, { checked ->
            selected.clear()
            if (checked) selected.addAll(NetworkSection.entries)
            rows.forEach { (section, row) -> row.isChecked = section in selected }
            all.isChecked = checked
            clearFormStatus()
        })
        body.addView(all, LinearLayout.LayoutParams(-1, -2))
        for (section in NetworkSection.entries) {
            lateinit var row: GroupedSwitchRow
            row = GroupedSwitchRow(context, context.getString(sectionLabel(section)), true, true, false, { checked ->
                if (checked) selected.add(section) else selected.remove(section)
                row.isChecked = checked
                all.isChecked = selected.size == NetworkSection.entries.size
                clearFormStatus()
            })
            rows[section] = row
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        return form(R.string.settings_proxy_restore_message, body, R.string.settings_proxy_restore_confirm) {
            if (selected.isEmpty()) context.getString(R.string.settings_proxy_restore_choose) else state.restore(selected)
        }
    }

    private fun chooseUrl(): View = column().apply {
        state.refresh()
        add(card(section.titleRes, description(R.string.settings_proxy_urls_message)))
        add(SettingsGroup(context).apply {
            RequestTarget.entries.forEach { target -> addView(editUrl(target), LinearLayout.LayoutParams(-1, -2)) }
        })
    }

    private fun editUrl(target: RequestTarget): View {
        val value = field(R.string.settings_proxy_url_override, state.overrides.urls[target].orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI, hint = state.defaultUrl(target))
        val saved = SettingsHeaderStatus(context).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            render(context.getString(R.string.settings_proxy_config_saved), R.drawable.ic_save_40, Colors.SELECTED)
        }
        lateinit var header: SettingsHeaderActions
        val save = SettingsCompactButton(context, context.getString(R.string.settings_proxy_save), Colors.SELECTED) {
            val problem = state.saveUrl(target, value.text)
            value.showError(problem)
            if (problem == null) {
                value.setText(state.overrides.urls[target].orEmpty())
            }
            header.showActions(problem == null)
        }.apply { deferClickUntilReleaseFeedback = true }
        header = SettingsHeaderActions(context, save, saved)
        value.onValueChanged = {
            value.showError(null)
            header.showActions(false)
        }
        return SettingsCard(context, null, targetLabel(target), content = value, trailing = header)
    }

    private fun checkChannel(): View {
        state.refresh()
        var scope = ProxyScope.NET
        val target = field(R.string.settings_proxy_check_target,
            state.snapshot.target(RequestTarget.NET_BASE_MAIMAI, state.defaultUrl(RequestTarget.NET_BASE_MAIMAI)),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val choices = linkedMapOf<ProxyScope, SettingsButton>()
        val scopeGroup = SettingsGroup(context)
        lateinit var renderCheck: () -> Unit
        for (value in ProxyScope.entries) {
            val button = SettingsPageViews.choice(context, context.getString(scopeLabel(value)), scope == value) {
                scope = value
                choices.forEach { (key, view) -> view.renderSelection(key == value) }
                renderCheck()
            }
            choices[value] = button
            scopeGroup.addView(button, LinearLayout.LayoutParams(-1, -2))
        }
        val results = ViewPrimitives.text(context, "", 13f, Colors.TEXT).apply {
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        lateinit var start: SettingsCheckButton
        start = SettingsCheckButton(context, context.getString(R.string.settings_proxy_check_start)) {
            state.startCheck(scope, target.text)
        }
        val cancel = SettingsPageViews.action(context, context.getString(R.string.settings_proxy_check_cancel), icon = null) {
            state.cancelCheck()
        }
        fun render() {
            val failed = state.checkSteps.any { it.status == NetworkCheckStatus.FAILED }
            val cancelled = state.checkCancelled
            val draftStale = state.checkReport?.let { it.scope != scope || it.targetUrl != target.text } == true
            val (status, label) = when {
                state.checking -> SettingsCheckButton.State.CHECKING to R.string.settings_proxy_check_running
                state.checkStale || draftStale -> SettingsCheckButton.State.WARNING to R.string.settings_proxy_check_stale
                cancelled -> SettingsCheckButton.State.WARNING to R.string.settings_proxy_check_cancelled
                failed -> SettingsCheckButton.State.FAILURE to R.string.settings_proxy_check_failed
                state.checkReport != null -> SettingsCheckButton.State.SUCCESS to R.string.settings_proxy_check_success
                else -> SettingsCheckButton.State.READY to R.string.settings_proxy_check_start
            }
            start.render(status, context.getString(label))
            cancel.isEnabled = state.checking
            target.setInputEnabled(!state.checking)
            choices.values.forEach { it.isEnabled = !state.checking }
            results.text = buildString {
                state.checkReport?.let { report ->
                    append(context.getString(R.string.settings_proxy_check_header,
                        DateFormat.format("HH:mm:ss", report.startedAtMs), report.revision))
                    append('\n').append(context.getString(scopeLabel(report.scope)))
                    append('\n').append(report.targetUrl)
                    if (state.checkSteps.isNotEmpty()) append("\n\n")
                }
                append(state.checkSteps.joinToString("\n") { stepText(it) })
            }
        }
        state.onCheckChanged = ::render
        renderCheck = ::render
        target.onValueChanged = { render() }
        val body = column().apply {
            add(description(R.string.settings_proxy_check_message))
            add(target); add(scopeGroup); add(start); add(cancel); add(results)
        }
        render()
        return card(section.titleRes, body)
    }

    private fun stepText(step: NetworkCheckStep): String {
        val stage = when (step.stage) {
            NetworkCheckStage.ENTRY_BIND -> R.string.settings_proxy_check_entry
            NetworkCheckStage.PROXY_BIND -> R.string.settings_proxy_check_proxy
            NetworkCheckStage.UPSTREAM -> R.string.settings_proxy_check_upstream
            NetworkCheckStage.TARGET -> R.string.settings_proxy_check_destination
        }
        val status = when (step.status) {
            NetworkCheckStatus.PASSED -> R.string.settings_proxy_check_passed
            NetworkCheckStatus.FAILED -> R.string.settings_proxy_check_rejected
            NetworkCheckStatus.SKIPPED -> R.string.settings_proxy_check_skipped
        }
        var text = context.getString(R.string.settings_proxy_check_result,
            context.getString(stage), context.getString(status), step.durationMs)
        step.reason?.let { text += " · " + when (it) {
            NetworkCheckReason.PORT_IN_USE -> context.getString(R.string.settings_proxy_check_port_used)
            NetworkCheckReason.INVALID_ROUTE -> context.getString(R.string.settings_proxy_check_invalid_route)
            NetworkCheckReason.SELF_ROUTE -> context.getString(R.string.settings_proxy_check_self_route)
            NetworkCheckReason.PROXY_AUTHENTICATION -> context.getString(R.string.settings_proxy_check_authentication)
            NetworkCheckReason.PROXY_REJECTED -> context.getString(R.string.settings_proxy_check_proxy_rejected)
            NetworkCheckReason.CONNECT_FAILED -> context.getString(R.string.settings_proxy_check_connect_failed)
            NetworkCheckReason.TLS_FAILED -> context.getString(R.string.settings_proxy_check_tls_failed)
            NetworkCheckReason.CANCELLED -> context.getString(R.string.settings_proxy_check_cancelled)
            NetworkCheckReason.TARGET_HTTP -> context.getString(R.string.settings_proxy_check_target_http, step.httpStatus)
        } }
        if (step.reason != NetworkCheckReason.TARGET_HTTP) step.httpStatus?.let {
            text = context.getString(R.string.settings_proxy_check_http, text, it)
        }
        return text
    }

    private fun description(message: Int) = ViewPrimitives.text(context, context.getString(message), 14f, Colors.TEXT)

    private fun card(title: Int, body: View) = SettingsCard(context, null, title, content = body)

    private fun form(message: Int, body: View, confirm: Int = R.string.settings_proxy_save,
        title: Int = section.titleRes, groupedOptions: List<View> = emptyList(), separateSave: Boolean = false,
        save: () -> String?,
    ): View {
        val status = ViewPrimitives.text(context, "", 14f, Colors.ERROR_TEXT).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val savedHeader = if (separateSave) SettingsHeaderActions(context, null,
            SettingsHeaderStatus(context).apply {
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
                render(context.getString(R.string.settings_proxy_config_saved), R.drawable.ic_save_40, Colors.SELECTED)
            }) else null
        val clearFeedback = {
            status.visibility = View.GONE
            savedHeader?.showActions(false)
            Unit
        }
        clearFormFeedback += clearFeedback
        body.allViews.filterIsInstance<SettingsTextField>().forEach { field ->
            field.onValueChanged = { clearFeedback() }
        }
        val action = SettingsButton(context, context.getString(confirm),
            if (separateSave) SettingsButton.Kind.ContainerAction else SettingsButton.Kind.Action,
            trailingIcon = null) {
            val problem = save()
            savedHeader?.showActions(problem == null)
            if (problem == null && savedHeader != null) status.visibility = View.GONE
            else {
                status.text = problem ?: context.getString(if (section == ProxySettingsSection.RESTORE)
                    R.string.settings_proxy_restored else R.string.settings_proxy_saved)
                status.setTextColor(if (problem == null) Colors.SELECTED else Colors.ERROR_TEXT)
                status.visibility = View.VISIBLE
            }
        }.apply { deferClickUntilReleaseFeedback = true }
        val card = SettingsCard(context, null, title, content = column().apply {
            add(description(message)); add(body); add(status)
            if (!separateSave) add(action)
        }, trailing = savedHeader)
        if (!separateSave) return card
        return column().apply {
            val formGroup = if (groupedOptions.isEmpty()) card else SettingsGroup(context).apply {
                addView(card, LinearLayout.LayoutParams(-1, -2))
                groupedOptions.forEach { addView(it, LinearLayout.LayoutParams(-1, -2)) }
            }
            addView(formGroup, LinearLayout.LayoutParams(-1, -2))
            add(action)
        }
    }

    override fun close() = state.close()

    private fun clearFormStatus() { clearFormFeedback.forEach { it() } }

    private fun scopeLabel(scope: ProxyScope) = when (scope) {
        ProxyScope.WECHAT -> R.string.settings_proxy_scope_wechat
        ProxyScope.NET -> R.string.settings_proxy_scope_net
        ProxyScope.PLATFORM -> R.string.settings_proxy_scope_platform
    }

    private fun sectionLabel(section: NetworkSection) = when (section) {
        NetworkSection.UPSTREAM -> R.string.settings_proxy_upstream
        NetworkSection.PORTS -> R.string.settings_proxy_ports
        NetworkSection.LISTEN -> R.string.settings_proxy_listen
        NetworkSection.URLS -> R.string.settings_proxy_urls
    }

    private fun targetLabel(target: RequestTarget) = when (target) {
        RequestTarget.NET_AUTH_MAIMAI -> R.string.settings_proxy_url_net_auth_mai
        RequestTarget.NET_AUTH_CHUNITHM -> R.string.settings_proxy_url_net_auth_chu
        RequestTarget.NET_BASE_MAIMAI -> R.string.settings_proxy_url_net_base_mai
        RequestTarget.NET_BASE_CHUNITHM -> R.string.settings_proxy_url_net_base_chu
        RequestTarget.UPLOAD_FISH_MAIMAI -> R.string.settings_proxy_url_upload_fish_mai
        RequestTarget.UPLOAD_FISH_CHUNITHM -> R.string.settings_proxy_url_upload_fish_chu
        RequestTarget.UPLOAD_LXNS_MAIMAI -> R.string.settings_proxy_url_upload_lxns_mai
        RequestTarget.UPLOAD_LXNS_CHUNITHM -> R.string.settings_proxy_url_upload_lxns_chu
        RequestTarget.OAUTH_FISH_ISSUER -> R.string.settings_proxy_url_oauth_fish
        RequestTarget.OAUTH_LXNS_ISSUER -> R.string.settings_proxy_url_oauth_lxns
    }
}
