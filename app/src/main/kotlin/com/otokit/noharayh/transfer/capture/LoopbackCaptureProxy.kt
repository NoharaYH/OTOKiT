package com.otokit.noharayh.transfer.capture

import com.otokit.noharayh.transfer.CaptureLocalHttpExchange
import com.otokit.noharayh.transfer.net.NetTargetPolicy
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.NetworkRoute
import com.otokit.noharayh.network.ProxyScope
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean

/** Task-scoped loopback capture. HTTPS CONNECT is passed through without inspecting TLS. */
internal class LoopbackCaptureProxy(
    private val entryPath: String,
    @Volatile private var authorization: URI? = null,
    private val onCallback: (Result<URI>) -> Unit,
    private val onEvent: (String) -> Unit = {},
    private val authorizationFactory: (() -> URI)? = null,
    private val callbackHtml: String,
    private val authorizationErrorHtml: String,
    private val network: NetworkSnapshot = NetworkSnapshot.defaults(),
) : Closeable {
    private val targets = NetTargetPolicy(network)
    private val authorizationLock = Any()
    @Volatile private var httpObserver: (CaptureLocalHttpExchange) -> Unit = {}
    private val closed = AtomicBoolean(false)
    private val accepted = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val capacity = Semaphore(8)
    private val workers = Executors.newFixedThreadPool(24)
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName(network.proxyHost), network.proxyPort))
    }
    private val entryServer = ServerSocket().also { entry ->
        try {
            entry.reuseAddress = true
            entry.bind(InetSocketAddress(InetAddress.getByName(network.entryHost), network.entryPort))
        } catch (error: Exception) { entry.close(); server.close(); throw error }
    }
    val port: Int get() = server.localPort
    val entryPort: Int get() = entryServer.localPort
    val entryUrl: String get() = "http://${network.entryHost}:$entryPort$entryPath"

    init {
        try {
            NetworkRoute.rejectSelfRoute(network, ProxyScope.WECHAT,
                listOf(server.localSocketAddress as InetSocketAddress, entryServer.localSocketAddress as InetSocketAddress))
        } catch (error: Exception) { server.close(); entryServer.close(); workers.shutdownNow(); throw error }
    }

    fun start() {
        accept(server)
        accept(entryServer)
    }

    fun observeHttp(observer: (CaptureLocalHttpExchange) -> Unit) { httpObserver = observer }

    private fun accept(listener: ServerSocket) {
        Thread({
            while (!closed.get()) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                if (!capacity.tryAcquire()) { socket.close(); continue }
                sockets.add(socket)
                if (closed.get()) { sockets.remove(socket); socket.close(); capacity.release(); continue }
                try {
                    workers.execute {
                        try { if (!closed.get()) handle(socket) } catch (error: Exception) {
                            if (!closed.get()) onEvent("proxy_error_${error.javaClass.simpleName}")
                        }
                        finally { sockets.remove(socket); socket.close(); capacity.release() }
                    }
                } catch (_: java.util.concurrent.RejectedExecutionException) {
                    sockets.remove(socket); socket.close(); capacity.release()
                }
            }
        }, "NetCaptureProxy").start()
    }

    private fun handle(socket: Socket) {
        socket.soTimeout = 30_000
        val input = socket.getInputStream().buffered()
        val header = readHeaders(input)
        val lines = header.split("\r\n")
        val request = lines.first().split(' ')
        require(request.size == 3)
        val method = request[0]
        val target = request[1]
        if (method == "CONNECT") {
            onEvent("connect")
            val uri = URI("https://$target")
            require(uri.host != null && uri.port in 1..65535 && uri.rawUserInfo == null &&
                uri.rawPath.isEmpty() && uri.rawQuery == null && uri.rawFragment == null)
            tunnel(socket, input, uri.host, uri.port, null)
            return
        }
        require(method == "GET" || method == "HEAD")
        val host = lines.drop(1).firstOrNull { it.startsWith("Host:", true) }
            ?.substringAfter(':')?.trim() ?: error("Missing Host")
        val uri = if (target.startsWith("http://")) URI(target) else URI("http://$host$target")
        require(uri.scheme == "http" && uri.rawUserInfo == null)
        val requestHeaders = lines.drop(1).filter { it.contains(':') }.map {
            it.substringBefore(':') to it.substringAfter(':').trim()
        }
        fun respond(status: Int, type: String, body: String, extra: String = "") {
            this.respond(socket, status, type, body, extra) { headers, bytes ->
                httpObserver(CaptureLocalHttpExchange(method, uri.toString(), requestHeaders, status, headers, bytes))
            }
        }
        if ((uri.host == network.proxyHost && uri.port == port) || (uri.host == network.entryHost && uri.port == entryPort)) {
            when (uri.rawPath) {
                "/health" -> respond(200, "text/plain", "ready")
                entryPath -> {
                    if (method == "HEAD") {
                        respond(200, "text/html", "", "Cache-Control: no-store\r\n")
                        return
                    }
                    onEvent("entry")
                    val current = try {
                        synchronized(authorizationLock) {
                            if (accepted.get()) {
                                respond(409, "text/plain", "Callback already consumed")
                                return
                            }
                            authorizationFactory?.invoke()?.also { authorization = it }
                                ?: authorization ?: error("Authorization unavailable")
                        }
                    } catch (error: Exception) {
                        if (accepted.compareAndSet(false, true)) {
                            try {
                                respond(502, "text/html; charset=utf-8", authorizationErrorHtml,
                                    "Cache-Control: no-store\r\n")
                            } finally {
                                // Let the owner close the channel only after the error response is sent.
                                onCallback(Result.failure(error))
                            }
                        } else respond(409, "text/plain", "Callback already consumed")
                        return
                    }
                    respond(302, "text/html; charset=utf-8", "", "Location: $current\r\nCache-Control: no-store\r\nPragma: no-cache\r\nExpires: 0\r\n")
                }
                else -> respond(404, "text/plain", "Not found")
            }
        } else if (NetCaptureProtocol.isCallback(uri, targets)) {
            val current = authorization
            if (method != "GET" || current == null || !NetCaptureProtocol.matchesAuthorization(uri, current, targets)) {
                onEvent("callback_mismatch")
                respond(400, "text/plain", "Callback does not match this session")
                return
            }
            if (accepted.compareAndSet(false, true)) {
                onEvent("callback")
                respond(200, "text/html; charset=utf-8", callbackHtml, "Cache-Control: no-store\r\n")
                onCallback(Result.success(uri))
            } else respond(409, "text/plain", "Callback already consumed")
        } else {
            require(uri.host != null && (uri.port == -1 || uri.port in 1..65535))
            val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            val upstream = NetworkRoute.usesUpstream(network, ProxyScope.WECHAT)
            val forward = buildString {
                append("$method ${if (upstream) uri.toASCIIString() else path} HTTP/1.1\r\n")
                lines.drop(1).filter { it.isNotBlank() && !it.startsWith("Proxy-", true) &&
                    !it.startsWith("Connection:", true) && !it.startsWith("Content-Length:", true) &&
                    !it.startsWith("Transfer-Encoding:", true) }.forEach { append(it).append("\r\n") }
                if (upstream) NetworkRoute.proxyAuthorization(network)?.let { append("Proxy-Authorization: $it\r\n") }
                append("Connection: close\r\n\r\n")
            }
            tunnel(socket, input, uri.host, if (uri.port == -1) 80 else uri.port, forward)
        }
    }

    private fun tunnel(client: Socket, input: InputStream, host: String, port: Int, header: String?) {
        var registered: Socket? = null
        try {
            val remote = NetworkRoute.openSocket(network, ProxyScope.WECHAT, host, port, 15_000,
                {
                    registered?.let(sockets::remove)
                    sockets.add(it); registered = it
                    if (closed.get()) { it.close(); throw java.io.IOException("Capture closed") }
                }, tunnel = header == null)
            remote.soTimeout = 120_000
            onEvent(if (NetworkRoute.usesUpstream(network, ProxyScope.WECHAT)) "forward_upstream" else "forward_direct")
            if (header == null) {
                client.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                client.getOutputStream().flush()
            } else remote.getOutputStream().write(header.toByteArray(Charsets.ISO_8859_1))
            val upstream = workers.submit {
                try { input.copyTo(remote.getOutputStream(), 16 * 1024) }
                catch (_: Exception) { }
                finally { runCatching { remote.shutdownOutput() } }
            }
            try { remote.getInputStream().copyTo(client.getOutputStream(), 16 * 1024) }
            finally { remote.close(); client.close(); upstream.cancel(true) }
        } finally { registered?.let { sockets.remove(it); it.close() } }
    }

    private fun respond(socket: Socket, status: Int, type: String, body: String, extra: String,
                        record: (List<Pair<String, String>>, ByteArray) -> Unit) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 $status Response\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
            "Connection: close\r\n${extra}\r\n"
        socket.getOutputStream().apply {
            write(header.toByteArray(Charsets.UTF_8))
            write(bytes); flush()
        }
        record(header.split("\r\n").drop(1).filter { it.contains(':') }.map {
            it.substringBefore(':') to it.substringAfter(':').trim()
        }, bytes)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        server.close()
        entryServer.close()
        sockets.forEach { runCatching { it.close() } }
        workers.shutdownNow()
    }

    companion object {
        /** Reads exactly through CRLFCRLF, preserving any buffered TLS/body bytes. */
        fun readHeaders(input: InputStream): String {
            val out = ByteArrayOutputStream()
            var tail = 0
            while (out.size() < 32 * 1024) {
                val byte = input.read()
                check(byte >= 0) { "Incomplete headers" }
                out.write(byte)
                tail = (tail shl 8) or byte
                if (tail == 0x0d0a0d0a) return out.toString("ISO-8859-1").removeSuffix("\r\n\r\n")
            }
            error("Headers too large")
        }
    }
}
