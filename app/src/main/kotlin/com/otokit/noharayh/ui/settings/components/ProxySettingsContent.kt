package com.otokit.noharayh.ui.settings.components

import android.content.Context
import android.text.InputType
import android.text.format.DateFormat
import android.view.View
import android.widget.LinearLayout
import com.otokit.noharayh.R
import com.otokit.noharayh.network.*
import com.otokit.noharayh.ui.constants.settings.SettingsComponentColors as Colors
import com.otokit.noharayh.ui.kit.common.ViewPrimitives
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsCheckButton
import com.otokit.noharayh.ui.kit.settings.SettingsGroup
import com.otokit.noharayh.ui.kit.settings.SettingsPageViews
import com.otokit.noharayh.ui.kit.settings.SettingsTextField
import java.io.Closeable

/** 代理设置的业务组合；表单草稿只在确认时交给状态持有者。 */
internal class ProxySettingsContent(private val context: Context, private val state: ProxySettingsState) : Closeable {
    private var dialog: MessageDialog? = null
    val view: View

    init {
        val rows = SettingsGroup(context)
        fun action(label: Int, click: () -> Unit) {
            rows.addView(SettingsPageViews.action(context, context.getString(label), deferClick = true, onClick = click),
                LinearLayout.LayoutParams(-1, -2))
        }
        action(R.string.settings_proxy_upstream, ::editUpstream)
        action(R.string.settings_proxy_ports, ::editPorts)
        action(R.string.settings_proxy_listen, ::editListen)
        action(R.string.settings_proxy_check, ::checkChannel)
        action(R.string.settings_proxy_restore, ::restore)
        action(R.string.settings_proxy_urls, ::chooseUrl)
        view = SettingsCard(context, R.drawable.ic_sync_24, R.string.settings_proxy, rows)
    }

    fun refresh() = state.refresh()

    private fun column() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        clipChildren = false
        clipToPadding = false
    }

    private fun LinearLayout.add(view: View) = addView(view, SettingsPageViews.gap(context))

    private fun field(label: Int, value: String, type: Int = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS,
                      hint: String? = null) = SettingsTextField(context, context.getString(label), value, type, hint)

    private fun editUpstream() {
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
            add(SettingsGroup(context).apply {
                ProxyScope.entries.forEach { scope ->
                    lateinit var row: GroupedSwitchRow
                    row = GroupedSwitchRow(context, context.getString(scopeLabel(scope)), true, true, scope in scopes, { checked ->
                        if (checked) scopes.add(scope) else scopes.remove(scope)
                        row.isChecked = checked
                    })
                    addView(row, LinearLayout.LayoutParams(-1, -2))
                }
            })
        }
        form(R.string.settings_proxy_upstream_message, body) {
            state.saveUpstream(host.text, port.text, username.text, password.text, scopes)
        }
    }

    private fun editPorts() {
        state.refresh()
        val overrides = state.overrides
        val defaults = NetworkSnapshot.defaults()
        val entry = field(R.string.settings_proxy_entry_port, overrides.entryPort?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER,
            context.getString(R.string.settings_proxy_default_value, defaults.entryPort.toString()))
        val proxy = field(R.string.settings_proxy_listen_port, overrides.proxyPort?.toString().orEmpty(), InputType.TYPE_CLASS_NUMBER,
            context.getString(R.string.settings_proxy_default_value, defaults.proxyPort.toString()))
        form(R.string.settings_proxy_ports_message, column().apply { add(entry); add(proxy) }) {
            state.savePorts(entry.text, proxy.text)
        }
    }

    private fun editListen() {
        state.refresh()
        val current = state.overrides
        val defaults = NetworkSnapshot.defaults()
        var entry = current.entryHost
        var proxy = current.proxyHost
        val body = column()
        fun choices(label: Int, default: String, initial: String?, selected: (String?) -> Unit) {
            body.add(ViewPrimitives.text(context, context.getString(label), 14f, Colors.TEXT, true))
            val group = SettingsGroup(context)
            val buttons = linkedMapOf<String?, SettingsButton>()
            for (host in listOf(null, "127.0.0.1", "127.0.0.2")) {
                val caption = host ?: context.getString(R.string.settings_proxy_use_default, default)
                val button = SettingsPageViews.choice(context, caption, initial == host) {
                    selected(host)
                    buttons.forEach { (value, view) -> view.renderSelection(value == host) }
                }
                buttons[host] = button
                group.addView(button, LinearLayout.LayoutParams(-1, -2))
            }
            body.add(group)
        }
        choices(R.string.settings_proxy_entry_host, defaults.entryHost, entry) { entry = it }
        choices(R.string.settings_proxy_listen_host, defaults.proxyHost, proxy) { proxy = it }
        form(R.string.settings_proxy_listen_message, body) { state.saveListen(entry, proxy) }
    }

    private fun restore() {
        val selected = linkedSetOf<NetworkSection>()
        val body = SettingsGroup(context)
        val rows = linkedMapOf<NetworkSection, GroupedSwitchRow>()
        lateinit var all: GroupedSwitchRow
        all = GroupedSwitchRow(context, context.getString(R.string.settings_proxy_restore_all), true, true, false, { checked ->
            selected.clear()
            if (checked) selected.addAll(NetworkSection.entries)
            rows.forEach { (section, row) -> row.isChecked = section in selected }
            all.isChecked = checked
        })
        body.addView(all, LinearLayout.LayoutParams(-1, -2))
        for (section in NetworkSection.entries) {
            lateinit var row: GroupedSwitchRow
            row = GroupedSwitchRow(context, context.getString(sectionLabel(section)), true, true, false, { checked ->
                if (checked) selected.add(section) else selected.remove(section)
                row.isChecked = checked
                all.isChecked = selected.size == NetworkSection.entries.size
            })
            rows[section] = row
            body.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        form(R.string.settings_proxy_restore_message, body, R.string.settings_proxy_restore_confirm) {
            if (selected.isEmpty()) context.getString(R.string.settings_proxy_restore_choose) else state.restore(selected)
        }
    }

    private fun chooseUrl() {
        val body = SettingsGroup(context)
        for (target in RequestTarget.entries) {
            body.addView(SettingsPageViews.action(context, context.getString(targetLabel(target)), deferClick = true) { editUrl(target) },
                LinearLayout.LayoutParams(-1, -2))
        }
        show(MessageDialog(context, R.drawable.ic_sync_24, context.getString(R.string.settings_proxy_urls_message),
            context.getString(R.string.settings_proxy_close), null, accentColor = Colors.TEXT,
            customContent = body, onConfirmed = {}))
    }

    private fun editUrl(target: RequestTarget) {
        state.refresh()
        val value = field(R.string.settings_proxy_url_override, state.overrides.urls[target].orEmpty(),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val body = column().apply {
            add(ViewPrimitives.text(context, context.getString(targetLabel(target)), 14f, Colors.TEXT, true))
            if (target == RequestTarget.OAUTH_FISH_ISSUER || target == RequestTarget.OAUTH_LXNS_ISSUER) {
                add(ViewPrimitives.text(context, context.getString(R.string.settings_proxy_url_issuer_message), 13f, Colors.TEXT))
            }
            add(ViewPrimitives.text(context, context.getString(R.string.settings_proxy_url_default), 13f, Colors.TEXT))
            add(ViewPrimitives.text(context, state.defaultUrl(target), 14f, Colors.MUTED).apply {
                setTextIsSelectable(true)
                contentDescription = context.getString(R.string.settings_proxy_url_default)
            })
            add(value)
        }
        form(R.string.settings_proxy_url_message, body) { state.saveUrl(target, value.text) }
    }

    private fun checkChannel() {
        state.refresh()
        var scope = ProxyScope.NET
        val target = field(R.string.settings_proxy_check_target,
            state.snapshot.target(RequestTarget.NET_BASE_MAIMAI, state.defaultUrl(RequestTarget.NET_BASE_MAIMAI)),
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val choices = linkedMapOf<ProxyScope, SettingsButton>()
        val scopeGroup = SettingsGroup(context)
        for (value in ProxyScope.entries) {
            val button = SettingsPageViews.choice(context, context.getString(scopeLabel(value)), scope == value) {
                scope = value
                choices.forEach { (key, view) -> view.renderSelection(key == value) }
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
        fun render() {
            val failed = state.checkSteps.any { it.status == NetworkCheckStatus.FAILED }
            val cancelled = state.checkReport?.cancelled == true
            val (status, label) = when {
                state.checking -> SettingsCheckButton.State.CHECKING to R.string.settings_proxy_check_running
                cancelled -> SettingsCheckButton.State.WARNING to R.string.settings_proxy_check_cancelled
                failed -> SettingsCheckButton.State.FAILURE to R.string.settings_proxy_check_failed
                state.checkReport != null -> SettingsCheckButton.State.SUCCESS to R.string.settings_proxy_check_success
                else -> SettingsCheckButton.State.READY to R.string.settings_proxy_check_start
            }
            start.render(status, context.getString(label))
            target.setInputEnabled(!state.checking)
            choices.values.forEach { it.isEnabled = !state.checking }
            results.text = buildString {
                state.checkReport?.let { report ->
                    append(context.getString(R.string.settings_proxy_check_header,
                        DateFormat.format("HH:mm:ss", report.startedAtMs), report.revision))
                    append('\n').append(report.targetUrl)
                    if (state.checkSteps.isNotEmpty()) append("\n\n")
                }
                append(state.checkSteps.joinToString("\n") { stepText(it) })
            }
        }
        state.onCheckChanged = ::render
        val body = column().apply { add(target); add(scopeGroup); add(start); add(results) }
        show(MessageDialog(context, R.drawable.ic_sync_24, context.getString(R.string.settings_proxy_check_message),
            context.getString(R.string.settings_proxy_close), null, accentColor = Colors.TEXT,
            customContent = body, onConfirmed = {}))
        render()
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

    private fun form(message: Int, body: View, confirm: Int = R.string.settings_proxy_save, save: () -> String?) {
        val error = ViewPrimitives.text(context, "", 14f, Colors.ERROR_TEXT).apply {
            visibility = View.GONE
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
        }
        val content = column().apply { add(body); add(error) }
        show(MessageDialog(context, R.drawable.ic_sync_24, context.getString(message), context.getString(confirm),
            context.getString(R.string.settings_proxy_cancel), accentColor = Colors.SELECTED,
            customContent = content, cancelOnTouchOutside = false, onSubmit = {
                val problem = save()
                error.text = problem.orEmpty()
                error.visibility = if (problem == null) View.GONE else View.VISIBLE
                problem == null
            }, onConfirmed = {}))
    }

    private fun show(next: MessageDialog) {
        val previous = dialog
        dialog = next
        previous?.dismiss()
        next.setOnDismissListener { if (dialog === next) {
            dialog = null
            state.cancelCheck()
            state.onCheckChanged = null
        } }
        next.showWithin(view.rootView.width)
    }

    override fun close() {
        state.close()
        val previous = dialog
        dialog = null
        previous?.dismiss()
    }

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
