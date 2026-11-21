package com.otokit.noharayh.transfer

import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.Socket
import java.net.ServerSocket
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class LoopbackCaptureProxyTest {
    private val callbackHtml = "<!doctype html><meta charset=utf-8><p>登录信息已接收</p><a href=\"otokit://transfer/return\">返回 OTOKiT</a>"
    private val authorizationErrorHtml = "<!doctype html><meta charset=utf-8><p>获取游戏授权入口失败，请返回 OTOKiT 重新开始。</p>"

    @Test fun fullTunnelCapacityDoesNotBlockLocalEntryOrCallback() {
        // Local idle TLS target; no external host is contacted.
        ServerSocket().use { target ->
            target.bind(InetSocketAddress("127.0.0.9", 443))
            val clients = mutableListOf<Socket>()
            val callbacks = AtomicInteger()
            val auth = URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=busy&redirect_uri=http%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx")
            LoopbackCaptureProxy("/entry", auth, onCallback = { callbacks.incrementAndGet() },
                callbackHtml = callbackHtml, authorizationErrorHtml = authorizationErrorHtml).use { proxy ->
                proxy.start()
                try {
                    repeat(8) {
                        val client = Socket("127.0.0.1", proxy.port).also(clients::add)
                        client.soTimeout = 2000
                        client.getOutputStream().write("CONNECT 127.0.0.9:443 HTTP/1.1\r\nHost: 127.0.0.9:443\r\n\r\n".toByteArray())
                        assertTrue(LoopbackCaptureProxy.readHeaders(client.getInputStream()).startsWith("HTTP/1.1 200"))
                    }
                    Socket("127.0.0.1", proxy.port).use { client ->
                        client.soTimeout = 2000
                        client.getOutputStream().write("CONNECT 127.0.0.9:443 HTTP/1.1\r\nHost: 127.0.0.9:443\r\n\r\n".toByteArray())
                        assertTrue(client.getInputStream().readBytes().toString(Charsets.UTF_8).startsWith("HTTP/1.1 503"))
                    }
                    fun request(target: String, host: String): String = Socket("127.0.0.1", proxy.port).use { client ->
                        client.soTimeout = 2000
                        client.getOutputStream().write("GET $target HTTP/1.1\r\nHost: $host\r\n\r\n".toByteArray())
                        client.getInputStream().readBytes().toString(Charsets.UTF_8)
                    }
                    assertTrue(request("http://127.0.0.2:8284/entry", "127.0.0.2:8284").startsWith("HTTP/1.1 302"))
                    assertTrue(request("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=test&state=busy",
                        "tgk-wcaime.wahlap.com").startsWith("HTTP/1.1 200"))
                    assertEquals(1, callbacks.get())
                } finally { clients.forEach(Socket::close) }
            }
        }
    }

    @Test fun failedAuthorizationReturnsCompleteErrorPageBeforeOwnerClosesTheProxy() {
        val failure = IOException("private-code-and-url")
        val delivered = AtomicReference<Throwable>()
        val closed = java.util.concurrent.CountDownLatch(1)
        lateinit var proxy: LoopbackCaptureProxy
        proxy = LoopbackCaptureProxy("/entry", onCallback = { result ->
            delivered.set(result.exceptionOrNull())
            proxy.close()
            closed.countDown()
        }, callbackHtml = callbackHtml, authorizationErrorHtml = authorizationErrorHtml, authorizationFactory = { throw failure })
        proxy.use {
            proxy.start()
            Socket("127.0.0.2", 8284).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write("GET /entry HTTP/1.1\r\nHost: 127.0.0.2:8284\r\n\r\n".toByteArray())
                val reply = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
                assertTrue(reply.startsWith("HTTP/1.1 502"))
                assertTrue(reply.contains("Cache-Control: no-store"))
                assertTrue(reply.endsWith("请返回 OTOKiT 重新开始。</p>"))
                assertFalse(reply.contains("private-code-and-url"))
                val length = reply.substringAfter("Content-Length: ").substringBefore("\r\n").toInt()
                assertEquals(length, reply.substringAfter("\r\n\r\n").toByteArray(Charsets.UTF_8).size)
            }
            assertTrue(closed.await(2, java.util.concurrent.TimeUnit.SECONDS))
            assertSame(failure, delivered.get())
        }
    }

    @Test fun repeatedRestartAndConcurrentDuplicateCallbacksDeliverExactlyOnce() {
        val clients = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            repeat(20) { round ->
                val count = AtomicInteger()
                val delivered = java.util.concurrent.CountDownLatch(1)
                val auth = URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=round$round&redirect_uri=http%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx")
                LoopbackCaptureProxy("/entry", auth, onCallback = { count.incrementAndGet(); delivered.countDown() }, callbackHtml = callbackHtml, authorizationErrorHtml = authorizationErrorHtml).use { proxy ->
                    proxy.start()
                    val replies = (1..2).map {
                        clients.submit<String> {
                            Socket("127.0.0.1", proxy.port).use { socket ->
                                socket.soTimeout = 2000
                                socket.getOutputStream().write(("GET http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=test&state=round$round HTTP/1.1\r\nHost: tgk-wcaime.wahlap.com\r\n\r\n").toByteArray())
                                socket.getOutputStream().flush()
                                socket.getInputStream().readBytes().toString(Charsets.UTF_8).lineSequence().first()
                            }
                        }
                    }.map { it.get(3, java.util.concurrent.TimeUnit.SECONDS) }
                    assertEquals(1, replies.count { it.startsWith("HTTP/1.1 200") })
                    assertEquals(1, replies.count { it.startsWith("HTTP/1.1 409") })
                    assertTrue(delivered.await(2, java.util.concurrent.TimeUnit.SECONDS))
                    assertEquals(1, count.get())
                }
            }
        } finally { clients.shutdownNow() }
    }

    @Test fun authorizationIsCreatedOnlyWhenTheUserOpensTheEntryAndRefreshesOnRetry() {
        val created = AtomicInteger()
        LoopbackCaptureProxy("/entry", onCallback = {}, callbackHtml = callbackHtml, authorizationErrorHtml = authorizationErrorHtml, authorizationFactory = {
            URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=${created.incrementAndGet()}")
        }).use { proxy ->
            proxy.start()
            assertEquals(0, created.get())
            fun open(): String = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 2000
                socket.getOutputStream().write("GET /entry HTTP/1.1\r\nHost: 127.0.0.1:${proxy.port}\r\n\r\n".toByteArray())
                socket.getOutputStream().flush()
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            assertTrue(open().contains("state=1"))
            assertTrue(open().contains("state=2"))
            assertEquals(2, created.get())
        }
    }
    @Test fun splitHeadersLeaveFollowingBytesUnread() {
        val input = ByteArrayInputStream("GET / HTTP/1.1\r\nHost: test\r\n\r\nTLS-DATA".toByteArray())
        assertEquals("GET / HTTP/1.1\r\nHost: test", LoopbackCaptureProxy.readHeaders(input))
        assertEquals("TLS-DATA", input.readBytes().toString(Charsets.UTF_8))
    }

    @Test fun invalidStateIsRejectedAndValidCallbackIsConsumedOnlyOnce() {
        val callbacks = AtomicInteger()
        val auth = URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=expected&redirect_uri=http%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx")
        LoopbackCaptureProxy("/start/test", auth, onCallback = { callbacks.incrementAndGet() }, callbackHtml = callbackHtml, authorizationErrorHtml = authorizationErrorHtml).use { proxy ->
            proxy.start()
            fun request(state: String): String = Socket("127.0.0.1", proxy.port).use { socket ->
                socket.soTimeout = 2000
                val path = "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=x&state=$state"
                val bytes = "GET $path HTTP/1.1\r\nHost: tgk-wcaime.wahlap.com\r\n\r\n".toByteArray()
                // Deliberately fragment the request line and headers.
                socket.getOutputStream().write(bytes, 0, 13)
                socket.getOutputStream().flush()
                socket.getOutputStream().write(bytes, 13, bytes.size - 13)
                socket.getOutputStream().flush()
                socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            }
            assertTrue(request("wrong").startsWith("HTTP/1.1 400"))
            val success = request("expected")
            assertTrue(success.startsWith("HTTP/1.1 200"))
            assertTrue(success.contains("Cache-Control: no-store"))
            assertTrue(success.endsWith(callbackHtml))
            assertFalse(success.contains("code=x"))
            assertTrue(request("expected").startsWith("HTTP/1.1 409"))
            assertEquals(1, callbacks.get())
        }
    }
}
