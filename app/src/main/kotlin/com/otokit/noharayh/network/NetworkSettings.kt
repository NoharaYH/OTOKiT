package com.otokit.noharayh.network

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URISyntaxException
import java.util.Collections

enum class ProxyScope { WECHAT, NET, PLATFORM }
enum class NetworkSection { UPSTREAM, PORTS, LISTEN, URLS }
enum class RequestTarget {
    NET_AUTH_MAIMAI, NET_AUTH_CHUNITHM, NET_BASE_MAIMAI, NET_BASE_CHUNITHM,
    UPLOAD_FISH_MAIMAI, UPLOAD_FISH_CHUNITHM, UPLOAD_LXNS_MAIMAI, UPLOAD_LXNS_CHUNITHM,
    OAUTH_FISH_ISSUER, OAUTH_LXNS_ISSUER,
}

object NetworkDefaults {
    const val ENTRY_HOST = "127.0.0.2"
    const val ENTRY_PORT = 8284
    const val PROXY_HOST = "127.0.0.1"
    const val PROXY_PORT = 0
}

data class UpstreamProxy(val host: String, val port: Int, val username: String? = null, val password: String? = null) {
    override fun toString() = "UpstreamProxy(host=$host, port=$port, authenticated=${username != null || password != null})"
}

/** null 表示未覆盖；端口 0 表示用户明确选择自动分配。 */
data class NetworkOverrides(
    val upstream: UpstreamProxy? = null,
    val scopes: Set<ProxyScope>? = null,
    val entryHost: String? = null,
    val entryPort: Int? = null,
    val proxyHost: String? = null,
    val proxyPort: Int? = null,
    val urls: Map<RequestTarget, String> = emptyMap(),
)

/** 已解析的任务输入；集合防御性复制，不随后续设置保存或恢复变化。 */
class NetworkSnapshot(
    val revision: Long = 0,
    val upstream: UpstreamProxy? = null,
    scopes: Set<ProxyScope> = emptySet(),
    val entryHost: String = NetworkDefaults.ENTRY_HOST,
    val entryPort: Int = NetworkDefaults.ENTRY_PORT,
    val proxyHost: String = NetworkDefaults.PROXY_HOST,
    val proxyPort: Int = NetworkDefaults.PROXY_PORT,
    urls: Map<RequestTarget, String> = emptyMap(),
) {
    val scopes: Set<ProxyScope> = Collections.unmodifiableSet(scopes.toSet())
    val urls: Map<RequestTarget, String> = Collections.unmodifiableMap(urls.toMap())

    fun target(key: RequestTarget, default: String): String = urls[key] ?: default

    fun toJson(): JSONObject = JSONObject().put("version", 1).put("revision", revision)
        .put("upstream", upstream?.json()).put("scopes", JSONArray(scopes.map { it.name }))
        .put("entryHost", entryHost).put("entryPort", entryPort).put("proxyHost", proxyHost).put("proxyPort", proxyPort)
        .put("urls", JSONObject(urls.mapKeys { it.key.name }))

    companion object {
        fun defaults() = NetworkSnapshot()
        fun fromJson(json: JSONObject): NetworkSnapshot {
            require(json.getInt("version") == 1) { "Unsupported network configuration version" }
            val snapshot = NetworkSnapshot(json.getLong("revision"), json.optJSONObject("upstream")?.proxy(),
                json.getJSONArray("scopes").scopes(), json.getString("entryHost"), json.getInt("entryPort"),
                json.getString("proxyHost"), json.getInt("proxyPort"), json.getJSONObject("urls").urls())
            validate(snapshot)
            return snapshot
        }
    }
}

/** 网络配置唯一所有者；存储只读写不透明字符串，不决定默认值与恢复语义。 */
class NetworkSettings(private val read: () -> String?, private val write: (String?) -> Unit) {
    private var revision = 0L
    private var custom = NetworkOverrides()

    init {
        read()?.let { raw ->
            val json = JSONObject(raw)
            require(json.getInt("version") == 1) { "Unsupported network configuration version" }
            revision = json.getLong("revision")
            val values = json.getJSONObject("overrides")
            custom = NetworkOverrides(values.optJSONObject("upstream")?.proxy(),
                values.optJSONArray("scopes")?.scopes(), values.stringOrNull("entryHost"), values.intOrNull("entryPort"),
                values.stringOrNull("proxyHost"), values.intOrNull("proxyPort"),
                values.optJSONObject("urls")?.urls().orEmpty())
            validate(resolve(custom))
        }
    }

    @Synchronized fun snapshot(): NetworkSnapshot = resolve(custom)
    @Synchronized fun overrides(): NetworkOverrides = custom.copy(
        scopes = custom.scopes?.let { Collections.unmodifiableSet(it.toSet()) },
        urls = Collections.unmodifiableMap(custom.urls.toMap()))

    @Synchronized fun saveUpstream(value: UpstreamProxy?, scopes: Set<ProxyScope>) =
        save(custom.copy(upstream = value?.copy(host = value.host.trim()), scopes = scopes.toSet()))

    @Synchronized fun savePorts(entryPort: Int?, proxyPort: Int?) =
        save(custom.copy(entryPort = entryPort, proxyPort = proxyPort))

    @Synchronized fun saveListen(entryHost: String?, proxyHost: String?) =
        save(custom.copy(entryHost = entryHost?.trim(), proxyHost = proxyHost?.trim()))

    @Synchronized fun saveUrl(target: RequestTarget, url: String?) {
        val urls = custom.urls.toMutableMap()
        if (url == null) urls.remove(target) else urls[target] = url.trim()
        save(custom.copy(urls = urls))
    }

    @Synchronized fun restore(sections: Set<NetworkSection>) {
        var next = custom
        if (NetworkSection.UPSTREAM in sections) next = next.copy(upstream = null, scopes = null)
        if (NetworkSection.PORTS in sections) next = next.copy(entryPort = null, proxyPort = null)
        if (NetworkSection.LISTEN in sections) next = next.copy(entryHost = null, proxyHost = null)
        if (NetworkSection.URLS in sections) next = next.copy(urls = emptyMap())
        save(next)
    }

    private fun save(next: NetworkOverrides) {
        validate(resolve(next))
        val values = JSONObject().put("upstream", next.upstream?.json())
            .put("scopes", next.scopes?.let { JSONArray(it.map { scope -> scope.name }) })
            .put("entryHost", next.entryHost).put("entryPort", next.entryPort)
            .put("proxyHost", next.proxyHost).put("proxyPort", next.proxyPort)
        if (next.urls.isNotEmpty()) values.put("urls", JSONObject(next.urls.mapKeys { it.key.name }))
        val encoded = JSONObject().put("version", 1).put("revision", revision + 1).put("overrides", values).toString()
        write(encoded)
        custom = next
        revision++
    }

    private fun resolve(value: NetworkOverrides): NetworkSnapshot = NetworkSnapshot(revision, value.upstream,
        value.scopes.orEmpty(), value.entryHost ?: NetworkDefaults.ENTRY_HOST, value.entryPort ?: NetworkDefaults.ENTRY_PORT,
        value.proxyHost ?: NetworkDefaults.PROXY_HOST, value.proxyPort ?: NetworkDefaults.PROXY_PORT, value.urls)
}

private fun UpstreamProxy.json() = JSONObject().put("host", host).put("port", port)
    .put("username", username).put("password", password)
private fun JSONObject.proxy() = UpstreamProxy(getString("host"), getInt("port"), stringOrNull("username"), stringOrNull("password"))
private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
private fun JSONObject.intOrNull(key: String): Int? = if (has(key) && !isNull(key)) getInt(key) else null
private fun JSONArray.scopes(): Set<ProxyScope> = (0 until length()).map { ProxyScope.valueOf(getString(it)) }.toSet()
private fun JSONObject.urls(): Map<RequestTarget, String> = keys().asSequence().associate { RequestTarget.valueOf(it) to getString(it) }

private fun validate(value: NetworkSnapshot) {
    require(value.entryPort in 0..65535 && value.proxyPort in 0..65535) { "本机端口需为 0～65535，0 表示自动分配。" }
    listOf(value.entryHost, value.proxyHost).forEach { host ->
        require(host == NetworkDefaults.ENTRY_HOST || host == NetworkDefaults.PROXY_HOST) { "监听地址目前仅支持 127.0.0.1 或 127.0.0.2。" }
    }
    require(value.entryPort == 0 || value.entryPort != value.proxyPort || value.entryHost != value.proxyHost) {
        "微信入口和本机代理不能使用相同的监听地址与固定端口。"
    }
    value.upstream?.let { proxy ->
        val host = try { URI("http://${proxy.host}") } catch (_: URISyntaxException) { null }
        require(proxy.host.isNotBlank() && proxy.host.none { it.isWhitespace() || it in ":/\\@?#[]" } && host?.host == proxy.host) {
            "上游代理地址请填写域名或 IPv4 地址，端口单独填写。"
        }
        require(proxy.port in 1..65535) { "上游代理端口需为 1～65535。" }
        require(listOfNotNull(proxy.username, proxy.password).none { it.contains('\r') || it.contains('\n') }) { "代理用户名和密码不能包含换行。" }
    }
    require(value.scopes.isEmpty() || value.upstream != null) { "请先填写上游代理，再选择需要经过代理的请求范围。" }
    value.urls.forEach { (key, raw) ->
        val uri = try { URI(raw) } catch (failure: URISyntaxException) { throw IllegalArgumentException("请输入有效的 HTTPS 请求地址。", failure) }
        require(uri.scheme.equals("https", true) && uri.host != null && uri.rawUserInfo == null && uri.rawQuery == null &&
            uri.rawFragment == null && uri.port in -1..65535 && uri.port != 0) { "请求地址必须使用 HTTPS，且不能包含账号密码、查询参数或片段。" }
        if (key == RequestTarget.NET_BASE_MAIMAI || key == RequestTarget.NET_BASE_CHUNITHM) require(raw.endsWith('/')) { "NET 基础地址需以 / 结尾。" }
    }
}
