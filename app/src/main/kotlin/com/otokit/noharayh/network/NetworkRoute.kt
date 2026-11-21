package com.otokit.noharayh.network

import okhttp3.Credentials
import okhttp3.OkHttpClient
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
import java.nio.charset.StandardCharsets

class NetworkRouteException(val reason: NetworkCheckReason, val httpStatus: Int? = null) :
    IOException("Network route failed: ${reason.name}")

/** 对原客户端仅应用路由；超时、一次性正文、重试及授权规则仍归原客户端所有者。 */
object NetworkRoute {
    fun usesUpstream(snapshot: NetworkSnapshot, scope: ProxyScope): Boolean =
        scope in snapshot.scopes && snapshot.upstream != null

    fun proxyAuthorization(snapshot: NetworkSnapshot): String? = snapshot.upstream?.let { proxy ->
        if (proxy.username == null && proxy.password == null) null
        else Credentials.basic(proxy.username.orEmpty(), proxy.password.orEmpty())
    }

    fun client(base: OkHttpClient, snapshot: NetworkSnapshot, scope: ProxyScope): OkHttpClient {
        val builder = base.newBuilder()
        if (!usesUpstream(snapshot, scope)) return builder.proxy(Proxy.NO_PROXY).proxyAuthenticator(okhttp3.Authenticator.NONE).build()
        val proxy = snapshot.upstream!!
        val address = InetSocketAddress(proxy.host, proxy.port)
        rejectSelfRoute(snapshot, scope, configuredListeners(snapshot), address)
        return builder.proxy(Proxy(Proxy.Type.HTTP, address)).proxyAuthenticator { _, response ->
            val authentication = proxyAuthorization(snapshot)
            val basic = response.challenges().any { it.scheme.equals("Basic", true) } ||
                response.header("Proxy-Authenticate") == "OkHttp-Preemptive"
            if (authentication == null || !basic || response.request.header("Proxy-Authorization") != null) null
            else response.request.newBuilder().header("Proxy-Authorization", authentication).build()
        }.build()
    }

    fun openSocket(snapshot: NetworkSnapshot, scope: ProxyScope, host: String, port: Int, timeoutMs: Int,
                   onSocket: (Socket) -> Unit, tunnel: Boolean = true): Socket {
        require(host.isNotBlank() && host.none { it == '\r' || it == '\n' } && port in 1..65535)
        if (!usesUpstream(snapshot, scope)) return connect(InetSocketAddress(host, port), timeoutMs, onSocket)
        val proxy = snapshot.upstream!!
        val address = InetSocketAddress(proxy.host, proxy.port)
        rejectSelfRoute(snapshot, scope, configuredListeners(snapshot), address)
        if (!tunnel) return connect(address, timeoutMs, onSocket)
        val authentication = proxyAuthorization(snapshot)
        val authority = if (host.contains(':')) "[$host]:$port" else "$host:$port"
        var withCredentials = false
        while (true) {
            val socket = connect(address, timeoutMs, onSocket)
            try {
                val request = buildString {
                    append("CONNECT $authority HTTP/1.1\r\nHost: $authority\r\n")
                    if (withCredentials) append("Proxy-Authorization: $authentication\r\n")
                    append("\r\n")
                }
                socket.getOutputStream().apply { write(request.toByteArray(StandardCharsets.ISO_8859_1)); flush() }
                val header = readProxyHeader(socket)
                val status = header.lineSequence().first().split(' ').getOrNull(1)?.toIntOrNull()
                    ?: throw NetworkRouteException(NetworkCheckReason.PROXY_REJECTED)
                if (status == 200) return socket
                val basic = header.lineSequence().filter { it.startsWith("Proxy-Authenticate:", true) }
                    .any { Regex("(?i)(?:^|,)\\s*Basic(?:\\s|$)").containsMatchIn(it.substringAfter(':').trim()) }
                if (status == 407 && basic && authentication != null && !withCredentials) {
                    withCredentials = true
                    socket.close()
                } else throw NetworkRouteException(if (status == 407) NetworkCheckReason.PROXY_AUTHENTICATION
                    else NetworkCheckReason.PROXY_REJECTED, status)
            } catch (failure: IOException) { socket.close(); throw failure }
        }
    }

    /** 实际自动分配端口由监听所有者传入，不能把任意 loopback 上游误认成自身。 */
    fun rejectSelfRoute(snapshot: NetworkSnapshot, scope: ProxyScope, listeners: Collection<InetSocketAddress>) {
        if (usesUpstream(snapshot, scope)) {
            val proxy = snapshot.upstream!!
            rejectSelfRoute(snapshot, scope, listeners, InetSocketAddress(proxy.host, proxy.port))
        }
    }

    private fun rejectSelfRoute(snapshot: NetworkSnapshot, scope: ProxyScope,
                                listeners: Collection<InetSocketAddress>, address: InetSocketAddress) {
        if (usesUpstream(snapshot, scope) && listeners.any { it.port > 0 && it.port == address.port &&
                it.address != null && it.address == address.address }) throw NetworkRouteException(NetworkCheckReason.SELF_ROUTE)
    }

    private fun configuredListeners(snapshot: NetworkSnapshot) = listOf(
        InetSocketAddress(snapshot.entryHost, snapshot.entryPort), InetSocketAddress(snapshot.proxyHost, snapshot.proxyPort))

    private fun connect(address: InetSocketAddress, timeoutMs: Int, onSocket: (Socket) -> Unit): Socket {
        val socket = Socket(Proxy.NO_PROXY)
        try {
            onSocket(socket)
            socket.connect(address, timeoutMs)
            socket.soTimeout = timeoutMs
            return socket
        } catch (failure: IOException) { socket.close(); throw failure }
        catch (failure: RuntimeException) { socket.close(); throw failure }
    }

    private fun readProxyHeader(socket: Socket): String {
        val data = ByteArrayOutputStream()
        var tail = 0
        while (data.size() < 32 * 1024) {
            val byte = socket.getInputStream().read()
            if (byte < 0) throw IOException("Upstream proxy closed before CONNECT completed")
            data.write(byte)
            tail = (tail shl 8) or byte
            if (tail == 0x0d0a0d0a) return data.toString(StandardCharsets.ISO_8859_1.name())
        }
        throw IOException("Upstream proxy header exceeds 32 KiB")
    }
}
