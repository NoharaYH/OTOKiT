package com.otokit.noharayh.transfer

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.network.*
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import com.otokit.noharayh.transfer.net.GameNetClient
import com.otokit.noharayh.transfer.net.GameNetPages
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URLEncoder
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class NetworkRoutingIntegrationTest {
    private fun telemetry(message: String) {
        Log.i("NetworkTelemetry", message)
        java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "network-routing-telemetry.txt")
            .appendText("${java.time.Instant.now()} $message\n")
    }
    @Test fun netAuthorizationCallbackLoginAndPagesUseOneFrozenRouteAndCustomOrigins() {
        lateinit var target: LocalHttpsFixture
        target = LocalHttpsFixture { request ->
            when {
                request.target.startsWith("/authorize") -> LocalHttpsFixture.Response(302, headers = mapOf("Location" to
                    "https://open.weixin.qq.com/connect/oauth2/authorize?state=fixture&redirect_uri=" +
                        URLEncoder.encode(target.url("/wc_auth/oauth/callback/maimai-dx"), "UTF-8")))
                request.target.startsWith("/wc_auth/") -> LocalHttpsFixture.Response(302,
                    headers = mapOf("Location" to target.url("/maimai/home/")))
                else -> LocalHttpsFixture.Response(body = "<!doctype html><p>fixture</p>", headers = mapOf("Content-Type" to "text/html"))
            }
        }
        target.use {
            LocalProxyFixture("fixture-user" to "fixture-password").use { upstream ->
                val network = NetworkSnapshot(revision = 31, upstream = upstream.upstream(), scopes = setOf(ProxyScope.NET),
                    urls = mapOf(RequestTarget.NET_AUTH_MAIMAI to target.url("/authorize"),
                        RequestTarget.NET_BASE_MAIMAI to target.url("/maimai/")), entryPort = 0)
                GameNetClient(network = network, transport = target.client).use { session ->
                    val authorization = session.authorizationUrl("maimai")
                    val callback = URI(target.url("/wc_auth/oauth/callback/maimai-dx?code=fixture-code&state=fixture").replaceFirst("https:", "http:"))
                    val received = AtomicReference<URI>()
                    LoopbackCaptureProxy("/entry", authorization, onCallback = { received.set(it.getOrThrow()) },
                        callbackHtml = "fixture callback", authorizationErrorHtml = "fixture error", network = network).use { capture ->
                        capture.start()
                        Socket(network.proxyHost, capture.port).use { socket ->
                            socket.soTimeout = 3000
                            socket.getOutputStream().write("GET $callback HTTP/1.1\r\nHost: ${callback.rawAuthority}\r\n\r\n".toByteArray())
                            assertTrue(socket.getInputStream().readBytes().decodeToString().startsWith("HTTP/1.1 200"))
                        }
                        assertEquals(callback, received.get())
                    }
                    session.login(callback) { assertEquals(200, it.status) }
                    val pages = GameNetPages(ScoreGame.MAIMAI, network)
                    assertEquals(target.url("/maimai/record/musicSort/search/?search=V&sort=1&playCheck=on&diff=3"), pages.uri(HtmlPageKind.MASTER).toString())
                    session.read(pages.uri(HtmlPageKind.PLAYER)) { assertTrue(it.stream.readBytes().isNotEmpty()) }
                    val before = upstream.requests.size
                    try { session.read(URI("https://unrelated.invalid/")) { } ; fail("Unrelated origin accepted") }
                    catch (_: IllegalArgumentException) { }
                    assertEquals(before, upstream.requests.size)
                }
                assertEquals(4, target.requests.size)
                assertTrue(target.requests.all { it.header("Proxy-Authorization") == null })
                assertTrue(upstream.requests.count { it.method == "CONNECT" } >= 4)
                assertTrue(target.failures.toString(), target.failures.isEmpty())
                telemetry("NET revision=31 auth+callback+login+page=PASS proxyExchanges=${upstream.requests.size} unrelatedOrigin=BLOCKED")
            }
        }
    }

    @Test fun loopbackForwardsPlainHttpAndTlsThroughAuthenticatedUpstreamUnderLoad() {
        val plainRequest = AtomicReference<LocalHttpsFixture.Request>()
        ServerSocket().use { plain ->
            plain.bind(InetSocketAddress("127.0.0.1", 0))
            val plainWorker = Executors.newSingleThreadExecutor()
            val plainFuture = plainWorker.submit {
                plain.accept().use { socket ->
                    plainRequest.set(LocalHttpsFixture.readRequest(socket.getInputStream()))
                    socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 5\r\nConnection: close\r\n\r\nplain".toByteArray())
                }
            }
            try {
                LocalHttpsFixture { LocalHttpsFixture.Response(body = "tls-fixture") }.use { target ->
                    LocalProxyFixture("fixture-user" to "fixture-password").use { upstream ->
                        val network = NetworkSnapshot(revision = 32, upstream = upstream.upstream(), scopes = setOf(ProxyScope.WECHAT),
                            entryHost = "127.0.0.1", entryPort = 0, proxyHost = "127.0.0.2", proxyPort = 0)
                        LoopbackCaptureProxy("/entry", onCallback = {}, callbackHtml = "fixture", authorizationErrorHtml = "fixture", network = network).use { capture ->
                            capture.start()
                            assertEquals("http://127.0.0.1:${capture.entryPort}/entry", capture.entryUrl)
                            val client = target.client.newBuilder().proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(network.proxyHost, capture.port))).build()
                            try {
                                client.newCall(Request.Builder().url("http://127.0.0.1:${plain.localPort}/plain").build()).execute().use {
                                    assertEquals("plain", it.body!!.string())
                                }
                                plainFuture.get(3, TimeUnit.SECONDS)
                                assertEquals("/plain", plainRequest.get().target)
                                assertNull(plainRequest.get().header("Proxy-Authorization"))
                                val workers = Executors.newFixedThreadPool(6)
                                val began = System.nanoTime()
                                try {
                                    val results = workers.invokeAll((1..48).map { index -> Callable {
                                        client.newCall(Request.Builder().url(target.url("/load/$index")).build()).execute().use {
                                            it.code == 200 && it.body!!.string() == "tls-fixture"
                                        }
                                    } }, 45, TimeUnit.SECONDS)
                                    assertTrue(results.all { !it.isCancelled && it.get() })
                                } finally { workers.shutdownNow() }
                                assertEquals(48, target.requests.size)
                                assertTrue(target.requests.all { it.header("Proxy-Authorization") == null })
                                assertTrue(target.failures.toString(), target.failures.isEmpty())
                                telemetry("WECHAT revision=32 plain=PASS TLS=48/48 concurrency=6 ms=${(System.nanoTime()-began)/1_000_000} upstream=${upstream.requests.size}")
                            } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
                        }
                    }
                }
            } finally { plainWorker.shutdownNow() }
        }
    }
}
