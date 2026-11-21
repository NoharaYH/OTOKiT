package com.otokit.noharayh.ui.settings.components

import android.os.Handler
import android.os.Looper
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.network.NetworkChannelCheck
import com.otokit.noharayh.network.NetworkCheckReport
import com.otokit.noharayh.network.NetworkCheckStep
import com.otokit.noharayh.network.NetworkSection
import com.otokit.noharayh.network.NetworkSettings
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.network.UpstreamProxy
import com.otokit.noharayh.transfer.NetRequestTargets
import com.otokit.noharayh.transfer.upload.UploadRequestTargets
import java.io.Closeable
import java.io.IOException

/** 页面确认状态及检查生命周期；配置校验、持久化和请求执行归网络公开入口。 */
internal class ProxySettingsState(
    private val settings: NetworkSettings,
    private val saveFailure: String,
    private val invalidPort: String,
    private val check: NetworkChannelCheck = NetworkChannelCheck(),
) : Closeable {
    var snapshot = settings.snapshot()
        private set
    val overrides get() = settings.overrides()
    var checking = false
        private set
    var checkSteps = emptyList<NetworkCheckStep>()
        private set
    var checkReport: NetworkCheckReport? = null
        private set
    var onCheckChanged: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())
    private var checkJob: Closeable? = null
    private var generation = 0

    fun refresh() { snapshot = settings.snapshot() }

    fun saveUpstream(host: String, port: String, username: String, password: String, scopes: Set<ProxyScope>): String? = save {
        val value = host.trim().takeIf { it.isNotEmpty() }?.let {
            UpstreamProxy(it, port.trim().toIntOrNull() ?: throw IllegalArgumentException(invalidPort),
                username.trim().takeIf(String::isNotEmpty), password.takeIf(String::isNotEmpty))
        }
        settings.saveUpstream(value, scopes)
    }

    fun savePorts(entry: String, proxy: String): String? = save {
        fun port(value: String): Int? = value.trim().takeIf(String::isNotEmpty)?.let {
            it.toIntOrNull() ?: throw IllegalArgumentException(invalidPort)
        }
        settings.savePorts(port(entry), port(proxy))
    }

    fun saveListen(entry: String?, proxy: String?): String? = save { settings.saveListen(entry, proxy) }

    fun saveUrl(target: RequestTarget, value: String): String? = save {
        val url = value.trim().takeIf(String::isNotEmpty)?.let { validateUrl(target, it) }
        validateUploadRoute(target, url)
        settings.saveUrl(target, url)
    }

    fun restore(sections: Set<NetworkSection>): String? = save { settings.restore(sections) }

    fun defaultUrl(target: RequestTarget): String = NetRequestTargets.defaultUrl(target)
        ?: UploadRequestTargets.defaultUrl(target)
        ?: OAuthProvider.entries.single { it.issuerTarget == target }.defaultIssuer

    private fun validateUrl(target: RequestTarget, value: String): String {
        when (target) {
            RequestTarget.NET_AUTH_MAIMAI, RequestTarget.NET_AUTH_CHUNITHM,
            RequestTarget.NET_BASE_MAIMAI, RequestTarget.NET_BASE_CHUNITHM -> NetRequestTargets.validate(target, value)
            RequestTarget.UPLOAD_FISH_MAIMAI, RequestTarget.UPLOAD_FISH_CHUNITHM,
            RequestTarget.UPLOAD_LXNS_MAIMAI, RequestTarget.UPLOAD_LXNS_CHUNITHM -> UploadRequestTargets.validate(target, value)
            RequestTarget.OAUTH_FISH_ISSUER, RequestTarget.OAUTH_LXNS_ISSUER ->
                return OAuthProvider.entries.single { it.issuerTarget == target }.validateIssuer(value)
        }
        return value
    }

    private fun validateUploadRoute(target: RequestTarget, value: String?) {
        val (provider, mai) = when (target) {
            RequestTarget.UPLOAD_FISH_MAIMAI -> OAuthProvider.DIVING_FISH to true
            RequestTarget.UPLOAD_FISH_CHUNITHM -> OAuthProvider.DIVING_FISH to false
            RequestTarget.UPLOAD_LXNS_MAIMAI -> OAuthProvider.LXNS to true
            RequestTarget.UPLOAD_LXNS_CHUNITHM -> OAuthProvider.LXNS to false
            else -> return
        }
        val current = settings.snapshot()
        val urls = current.urls.toMutableMap()
        if (value == null) urls.remove(target) else urls[target] = value
        val draft = NetworkSnapshot(current.revision, current.upstream, current.scopes, current.entryHost,
            current.entryPort, current.proxyHost, current.proxyPort, urls)
        UploadRequestTargets.validateRoute(draft, provider, mai)
    }

    private fun save(write: () -> Unit): String? = try {
        write()
        refresh()
        null
    } catch (error: IllegalArgumentException) {
        error.message ?: saveFailure
    } catch (_: IOException) {
        saveFailure
    } catch (_: IllegalStateException) {
        saveFailure
    }

    fun startCheck(scope: ProxyScope, targetUrl: String) {
        cancelCheck()
        val request = generation
        checking = true
        checkSteps = emptyList()
        checkReport = null
        onCheckChanged?.invoke()
        checkJob = check.start(snapshot, scope, targetUrl, { step -> main.post {
            if (request != generation) return@post
            checkSteps = checkSteps + step
            onCheckChanged?.invoke()
        } }, { report -> main.post {
            if (request != generation) return@post
            checking = false
            checkJob = null
            checkReport = report
            onCheckChanged?.invoke()
        } })
    }

    fun cancelCheck() {
        generation++
        checkJob?.close()
        checkJob = null
        checking = false
    }

    override fun close() { cancelCheck(); onCheckChanged = null }
}
