package com.otokit.noharayh.transfer

import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.net.Socket
import java.net.URI
import java.util.concurrent.atomic.AtomicInteger

class LoopbackCaptureProxyTest {
    private val callbackHtml = "<!doctype html><meta charset=utf-8><p>登录信息已接收</p><a href=\"otokit://transfer/return\">返回 OTOKiT</a>"
    @Test fun repeatedRestartAndConcurrentDuplicateCallbacksDeliverExactlyOnce() {
        val clients = java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            repeat(20) { round ->
                val count = AtomicInteger()
                val delivered = java.util.concurrent.CountDownLatch(1)
                val auth = URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=round$round&redirect_uri=http%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx")
                LoopbackCaptureProxy("/entry", auth, onCallback = { count.incrementAndGet(); delivered.countDown() }, callbackHtml = callbackHtml).use { proxy ->
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
        LoopbackCaptureProxy("/entry", onCallback = {}, callbackHtml = callbackHtml, authorizationFactory = {
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
        LoopbackCaptureProxy("/start/test", auth, onCallback = { callbacks.incrementAndGet() }, callbackHtml = callbackHtml).use { proxy ->
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
