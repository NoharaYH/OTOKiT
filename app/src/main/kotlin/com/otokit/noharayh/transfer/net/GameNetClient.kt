package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.transfer.capture.NetCaptureProtocol
import com.otokit.noharayh.transfer.CaptureHttpTrace
import com.otokit.noharayh.transfer.CaptureProtocolIssue
import com.otokit.noharayh.transfer.TransferRecording
import com.otokit.noharayh.transfer.NetRequestTargets
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.NetworkRoute
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.network.RequestTarget
import okhttp3.Call
import okhttp3.ConnectionSpec
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.TlsVersion
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

/** Task-local transport. Compatibility profile follows the existing Dart Android crawler. */
internal class GameNetClient(
    private val onResponse: (String, Int) -> Unit = { _, _ -> },
    private val onRetry: () -> Unit = {},
    private val onTrace: (CaptureHttpTrace) -> Unit = {},
    private val onRejectedResponse: (NetSession.Page) -> Unit = {},
    recording: TransferRecording? = null,
    private val network: NetworkSnapshot = NetworkSnapshot.defaults(),
    transport: OkHttpClient = OkHttpClient(),
) : NetSession {
    private val closed = AtomicBoolean(false)
    @Volatile private var active: Call? = null
    private val cookies = HostCookies()
    private val targets = NetTargetPolicy(network)
    private var callbackDestination: URI? = null
    private val client = NetworkRoute.client(NetExchangeGuard.configure(transport.newBuilder())
        .connectTimeout(120, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .cookieJar(cookies).cache(null)
        .connectionSpecs(listOf(ConnectionSpec.Builder(ConnectionSpec.COMPATIBLE_TLS)
            .tlsVersions(TlsVersion.TLS_1_2, TlsVersion.TLS_1_1, TlsVersion.TLS_1_0)
            .allEnabledCipherSuites().build(), ConnectionSpec.CLEARTEXT))
        .pingInterval(3, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false)
        .addInterceptor { chain -> chain.proceed(chain.request().newBuilder().header("Cache-Control", "no-cache").build()) }
        .addNetworkInterceptor { chain ->
            val uri = chain.request().url.toUri()
            require(NetCaptureProtocol.isNetDestination(uri, targets)) { "Unexpected NET destination" }
            val started = System.nanoTime()
            chain.proceed(chain.request()).also { response ->
                onResponse(uri.host, response.code)
                onTrace(CaptureHttpTrace(chain.request().method, NetTraceEndpoint.classify(uri), response.code,
                    response.protocol.toString(), response.handshake?.tlsVersion?.javaName,
                    response.header("Location")?.let { runCatching { NetTraceEndpoint.classify(uri.resolve(it)) }.getOrDefault("INVALID_LOCATION") },
                    (System.nanoTime() - started) / 1_000_000))
            }
        }.apply { recording?.let { addNetworkInterceptor(it.interceptor("NET")) } }.build(), network, ProxyScope.NET)

    override fun authorizationUrl(game: String): URI {
        require(game in listOf("maimai", "chunithm"))
        var current = NetRequestTargets.resolve(network,
            if (game == "maimai") RequestTarget.NET_AUTH_MAIMAI else RequestTarget.NET_AUTH_CHUNITHM)
        repeat(12) {
            if (current.host == "open.weixin.qq.com") {
                val rewritten = NetCaptureProtocol.rewriteWechatRedirect(current, targets)
                callbackDestination = URI(java.net.URLDecoder.decode(current.rawQuery.split('&')
                    .single { it.startsWith("redirect_uri=") }.substringAfter('='), "UTF-8"))
                return rewritten
            }
            val request = browserHeaders(Request.Builder().url(current.toString()), MOBILE_UA)
                .header("X-Requested-With", "com.tencent.mm")
                .header("Accept-Encoding", "gzip, deflate").build()
            execute(request) { response ->
                if (response.code !in 300..399) throw NetHttpException(response.code)
                current = current.resolve(response.header("Location") ?: error("Missing authorization redirect"))
            }
        }
        error("Too many authorization redirects")
    }

    override fun login(callback: URI, saveDiagnostic: (NetSession.Page) -> Unit) {
        cookies.clear()
        // The reference constructs a fresh OkHttp client after authorization/VPN teardown.
        client.connectionPool.evictAll()
        var request = browserHeaders(Request.Builder().url(NetCaptureProtocol.secureCallback(callback, targets, callbackDestination).toString()), DESKTOP_UA)
            .header("Connection", "keep-alive")
            .header("Accept-Encoding", "gzip, deflate, br").build()
        // Follow each redirect explicitly; a callback is never transparently replayed.
        val visited = mutableSetOf<String>()
        repeat(12) {
            if (!visited.add(request.url.toString())) throw NetProtocolException(CaptureProtocolIssue.REDIRECT_LOOP)
            var redirect: URI? = null
            execute(request) { response ->
                if (response.code in 300..399) {
                    redirect = request.url.toUri().resolve(response.header("Location") ?: error("Missing login redirect"))
                } else {
                    page(response) { saveDiagnostic(it) }
                    if (response.code != 200) throw NetHttpException(response.code)
                }
            }
            val next = redirect ?: return
            request = request.newBuilder().url(next.toString()).build()
        }
        error("Too many login redirects")
    }

    override fun <T> read(uri: URI, post: String?, consume: (NetSession.Page) -> T): T {
        var current = uri
        var body = post
        repeat(12) {
            val request = Request.Builder().url(current.toString()).apply {
                body?.let { post(it.toRequestBody("application/x-www-form-urlencoded".toMediaType())) }
            }.build()
            var redirect: URI? = null
            var result: T? = null
            var consumed = false
            NetReadRetry({ closed.get() }, onRetry).run(replaySafe = body == null) { execute(request) { response ->
                if (response.code in 300..399) {
                    redirect = current.resolve(response.header("Location") ?: error("Missing NET redirect"))
                    if (response.code in listOf(301, 302, 303)) body = null
                } else {
                    if (response.code != 200) {
                        try { page(response) { onRejectedResponse(it) } }
                        catch (error: java.io.IOException) { throw NetHttpException(response.code).apply { initCause(error) } }
                        throw NetHttpException(response.code)
                    }
                    result = page(response, consume)
                    consumed = true
                }
            } }
            if (consumed) { @Suppress("UNCHECKED_CAST") return result as T }
            current = redirect ?: error("Missing NET redirect")
        }
        error("Too many NET redirects")
    }

    private fun <T> page(response: okhttp3.Response, consume: (NetSession.Page) -> T): T {
        val body = response.body ?: error("Missing NET body")
        val stream = when (response.header("Content-Encoding")?.lowercase()) {
            "gzip" -> GZIPInputStream(body.byteStream())
            "deflate" -> InflaterInputStream(body.byteStream())
            else -> body.byteStream()
        }
        return stream.use { consume(NetSession.Page(response.request.url.toUri(), response.header("Content-Type").orEmpty(), it, response.code)) }
    }

    private fun <T> execute(request: Request, consume: (okhttp3.Response) -> T): T {
        check(!closed.get()) { "Session closed" }
        require(NetCaptureProtocol.isNetDestination(request.url.toUri(), targets)) { "Unexpected NET destination" }
        val call = client.newCall(NetExchangeGuard.schedule(request))
        active = call
        if (closed.get()) call.cancel()
        try { return call.execute().use(consume) }
        finally { if (active === call) active = null }
    }

    override fun close() {
        closed.set(true); active?.cancel(); cookies.clear()
        client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }

    /** Legacy crawler merged cookies by host/name; retained for the controlled comparison. */
    private class HostCookies : CookieJar {
        private val store = mutableMapOf<String, MutableMap<String, Cookie>>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val host = store.getOrPut(url.host) { linkedMapOf() }
            cookies.forEach { host[it.name] = it }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host]?.values?.toList().orEmpty()
        @Synchronized fun clear() = store.clear()
    }

    private fun browserHeaders(builder: Request.Builder, userAgent: String): Request.Builder = builder
        .header("User-Agent", userAgent)
        .header("Upgrade-Insecure-Requests", "1")
        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.9")
        .header("Sec-Fetch-Site", "none").header("Sec-Fetch-Mode", "navigate")
        .header("Sec-Fetch-User", "?1").header("Sec-Fetch-Dest", "document")
        .header("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7")

    private companion object {
        const val MOBILE_UA = "Mozilla/5.0 (Linux; Android 12; IN2010 Build/RKQ1.211119.001; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/86.0.4240.99 XWEB/4317 MMWEBSDK/20220903 Mobile Safari/537.36 MMWEBID/363 MicroMessenger/8.0.28.2240(0x28001C57) WeChat/arm64 Weixin NetType/WIFI Language/zh_CN ABI/arm64"
        const val DESKTOP_UA = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.138 Safari/537.36 NetType/WIFI MicroMessenger/7.0.20.1781(0x6700143B) WindowsWechat(0x6307001e)"
    }
}
