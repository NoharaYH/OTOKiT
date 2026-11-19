package com.otokit.noharayh.transfer.capture

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
    private val onCallback: (URI) -> Unit,
    private val onEvent: (String) -> Unit = {},
    private val authorizationFactory: (() -> URI)? = null,
    private val callbackHtml: String,
) : Closeable {
    private val authorizationLock = Any()
    private val closed = AtomicBoolean(false)
    private val accepted = AtomicBoolean(false)
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val capacity = Semaphore(8)
    private val workers = Executors.newFixedThreadPool(24)
    private val server = ServerSocket().apply {
        reuseAddress = true
        bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
    }
    private val entryServer = try {
        ServerSocket().apply {
            reuseAddress = true
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.2"), 8284))
        }
    } catch (error: Exception) { server.close(); throw error }
    val port: Int get() = server.localPort

    fun start() {
        accept(server)
        accept(entryServer)
    }

    private fun accept(listener: ServerSocket) {
        Thread({
            while (!closed.get()) {
                val socket = try { listener.accept() } catch (_: Exception) { break }
                if (!capacity.tryAcquire()) { socket.close(); continue }
                sockets.add(socket)
                try {
                    workers.execute {
                        try { handle(socket) } catch (error: Exception) {
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
            require(uri.host != null && uri.port == 443 && uri.rawUserInfo == null && uri.rawPath.isEmpty())
            tunnel(socket, input, uri.host, 443, null)
            return
        }
        require(method == "GET" || method == "HEAD")
        val host = lines.drop(1).firstOrNull { it.startsWith("Host:", true) }
            ?.substringAfter(':')?.trim() ?: error("Missing Host")
        val uri = if (target.startsWith("http://")) URI(target) else URI("http://$host$target")
        require(uri.scheme == "http" && uri.rawUserInfo == null)
        if ((uri.host == "127.0.0.1" && uri.port == port) || (uri.host == "127.0.0.2" && uri.port == 8284)) {
            when (uri.rawPath) {
                "/health" -> respond(socket, 200, "text/plain", "ready")
                entryPath -> {
                    if (method == "HEAD") {
                        respond(socket, 200, "text/html", "", "Cache-Control: no-store\r\n")
                        return
                    }
                    onEvent("entry")
                    val current = synchronized(authorizationLock) {
                        check(!accepted.get()) { "Callback already consumed" }
                        authorizationFactory?.invoke()?.also { authorization = it }
                            ?: authorization ?: error("Authorization unavailable")
                    }
                    respond(socket, 302, "text/html; charset=utf-8", "", "Location: $current\r\nCache-Control: no-store\r\nPragma: no-cache\r\nExpires: 0\r\n")
                }
                else -> respond(socket, 404, "text/plain", "Not found")
            }
        } else if (NetCaptureProtocol.isCallback(uri)) {
            val current = authorization
            if (method != "GET" || current == null || !NetCaptureProtocol.matchesAuthorization(uri, current)) {
                onEvent("callback_mismatch")
                respond(socket, 400, "text/plain", "Callback does not match this session")
                return
            }
            if (accepted.compareAndSet(false, true)) {
                onEvent("callback")
                respond(socket, 200, "text/html; charset=utf-8", callbackHtml, "Cache-Control: no-store\r\n")
                onCallback(uri)
            } else respond(socket, 409, "text/plain", "Callback already consumed")
        } else {
            require(uri.host != null && uri.port in listOf(-1, 80))
            val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
            val forward = buildString {
                append("$method $path HTTP/1.1\r\n")
                lines.drop(1).filter { it.isNotBlank() && !it.startsWith("Proxy-", true) &&
                    !it.startsWith("Connection:", true) && !it.startsWith("Content-Length:", true) &&
                    !it.startsWith("Transfer-Encoding:", true) }.forEach { append(it).append("\r\n") }
                append("Connection: close\r\n\r\n")
            }
            tunnel(socket, input, uri.host, 80, forward)
        }
    }

    private fun tunnel(client: Socket, input: InputStream, host: String, port: Int, header: String?) {
        val remote = Socket()
        sockets.add(remote)
        try {
            remote.connect(InetSocketAddress(host, port), 15_000)
            remote.soTimeout = 120_000
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
        } finally { sockets.remove(remote); remote.close() }
    }

    private fun respond(socket: Socket, status: Int, type: String, body: String, extra: String = "") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        socket.getOutputStream().apply {
            write(("HTTP/1.1 $status Response\r\nContent-Type: $type\r\nContent-Length: ${bytes.size}\r\n" +
                "Connection: close\r\n${extra}\r\n").toByteArray(Charsets.UTF_8))
            write(bytes); flush()
        }
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
