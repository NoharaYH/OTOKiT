package com.otokit.noharayh.network

import androidx.test.platform.app.InstrumentationRegistry
import okhttp3.OkHttpClient
import java.io.Closeable
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/** A real TLS peer with a test-only CA. Production trust and hostname checks are untouched. */
internal class LocalHttpsFixture(private val reply: (Request) -> Response) : Closeable {
    data class Request(val method: String, val target: String, val headers: Map<String, String>, val body: ByteArray) {
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
    }
    data class Response(val code: Int = 200, val body: String = "", val headers: Map<String, String> = emptyMap())
    private val store = KeyStore.getInstance("PKCS12").apply {
        InstrumentationRegistry.getInstrumentation().context.assets.open("network/localhost.p12").use {
            load(it, "fixture-only".toCharArray())
        }
    }
    private val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
        init(store, "fixture-only".toCharArray())
    }
    private val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(store) }
        .trustManagers.filterIsInstance<X509TrustManager>().single()
    private val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, arrayOf(trust), null) }
    private val server = tls.serverSocketFactory.createServerSocket() as javax.net.ssl.SSLServerSocket
    private val workers = Executors.newCachedThreadPool()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    val requests = CopyOnWriteArrayList<Request>()
    val failures = CopyOnWriteArrayList<Throwable>()
    val client: OkHttpClient = OkHttpClient.Builder().sslSocketFactory(tls.socketFactory, trust)
        .retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()
    val port: Int get() = server.localPort
    fun url(path: String = "/") = "https://127.0.0.1:$port$path"

    init {
        server.bind(InetSocketAddress("127.0.0.1", 0))
        workers.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                sockets.add(socket)
                workers.execute {
                    try {
                        socket.soTimeout = 5000
                        val request = readRequest(socket.getInputStream())
                        requests.add(request)
                        val response = reply(request)
                        val bytes = response.body.toByteArray()
                        socket.getOutputStream().apply {
                            write(("HTTP/1.1 ${response.code} Fixture\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n" +
                                response.headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray())
                            write(bytes); flush()
                        }
                    } catch (error: Exception) { if (!server.isClosed) failures.add(error) }
                    finally { sockets.remove(socket); socket.close() }
                }
            }
        }
    }

    override fun close() {
        server.close(); sockets.forEach { it.close() }; workers.shutdownNow()
        workers.awaitTermination(2, TimeUnit.SECONDS)
        client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
    }

    companion object {
        fun readHeader(input: InputStream): String {
            val bytes = java.io.ByteArrayOutputStream()
            var tail = 0
            while (bytes.size() < 32768) {
                val value = input.read()
                check(value >= 0) { "Incomplete fixture request" }
                bytes.write(value); tail = (tail shl 8) or value
                if (tail == 0x0d0a0d0a) return bytes.toString("ISO-8859-1").removeSuffix("\r\n\r\n")
            }
            error("Fixture header too large")
        }
        fun readRequest(input: InputStream): Request {
            val lines = readHeader(input).split("\r\n")
            val first = lines.first().split(' ')
            val headers = lines.drop(1).associate { it.substringBefore(':') to it.substringAfter(':').trim() }
            val length = headers.entries.firstOrNull { it.key.equals("Content-Length", true) }?.value?.toInt() ?: 0
            val body = ByteArray(length)
            var read = 0
            while (read < length) { val n = input.read(body, read, length - read); check(n > 0); read += n }
            return Request(first[0], first[1], headers, body)
        }
    }
}

/** Controlled HTTP proxy which actually forwards TLS and ordinary HTTP over local sockets. */
internal class LocalProxyFixture(private val credentials: Pair<String, String>? = null) : Closeable {
    private val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
    private val workers = Executors.newCachedThreadPool()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    val requests = CopyOnWriteArrayList<LocalHttpsFixture.Request>()
    val port: Int get() = server.localPort
    val openSockets: Int get() = sockets.count { !it.isClosed }
    fun upstream() = UpstreamProxy("127.0.0.1", port, credentials?.first, credentials?.second)

    init {
        workers.execute {
            while (!server.isClosed) {
                val socket = try { server.accept() } catch (_: java.io.IOException) { break }
                sockets.add(socket)
                workers.execute {
                    var remote: Socket? = null
                    try {
                        socket.soTimeout = 5000
                        val input = socket.getInputStream()
                        val request = LocalHttpsFixture.readRequest(input)
                        requests.add(request)
                        val auth = credentials?.let { okhttp3.Credentials.basic(it.first, it.second) }
                        if (auth != null && request.header("Proxy-Authorization") != auth) {
                            socket.getOutputStream().write(("HTTP/1.1 407 Proxy Authentication Required\r\n" +
                                "Proxy-Authenticate: Basic realm=fixture\r\nContent-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
                            return@execute
                        }
                        val uri = URI(if (request.method == "CONNECT") "https://${request.target}" else request.target)
                        check(uri.host == "127.0.0.1" || uri.host == "localhost") { "Fixture cannot contact external hosts" }
                        remote = Socket(uri.host, if (uri.port == -1) 80 else uri.port).also { sockets.add(it) }
                        val target = remote
                        if (request.method == "CONNECT") {
                            socket.getOutputStream().write("HTTP/1.1 200 Connection Established\r\n\r\n".toByteArray())
                        } else {
                            val path = uri.rawPath.ifEmpty { "/" } + (uri.rawQuery?.let { "?$it" } ?: "")
                            val headers = request.headers.filterKeys { !it.startsWith("Proxy-", true) }
                            target.getOutputStream().write(("${request.method} $path HTTP/1.1\r\n" +
                                headers.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n").toByteArray())
                            target.getOutputStream().write(request.body)
                        }
                        val upstream = workers.submit {
                            try { input.copyTo(target.getOutputStream()) } catch (_: java.io.IOException) { }
                        }
                        try { target.getInputStream().copyTo(socket.getOutputStream()) }
                        finally { upstream.cancel(true) }
                    } catch (_: java.io.IOException) { }
                    finally {
                        remote?.let { sockets.remove(it); it.close() }
                        sockets.remove(socket); socket.close()
                    }
                }
            }
        }
    }
    override fun close() {
        server.close(); sockets.forEach { it.close() }; workers.shutdownNow()
        workers.awaitTermination(2, TimeUnit.SECONDS)
    }
}
