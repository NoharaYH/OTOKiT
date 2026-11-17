package com.otokit.noharayh.transfer.net

import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

class NetExchangeGuardTest {
    private fun server(action: (Socket, Int) -> Unit, test: (String, AtomicInteger) -> Unit) {
        val count = AtomicInteger()
        val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val sockets = CopyOnWriteArrayList<Socket>()
        val worker = Thread {
            while (!server.isClosed) {
                try {
                    server.accept().use { socket ->
                        sockets.add(socket)
                        val reader = socket.getInputStream().bufferedReader()
                        if (reader.readLine() != null) {
                            while (true) { val line = reader.readLine(); if (line.isNullOrEmpty()) break }
                            action(socket, count.incrementAndGet())
                        }
                        sockets.remove(socket)
                    }
                } catch (_: IOException) { if (!server.isClosed) throw AssertionError("Local fixture failed") }
            }
        }.apply { isDaemon = true; start() }
        try { test("http://127.0.0.1:${server.localPort}/", count) }
        finally { server.close(); sockets.forEach { it.close() }; worker.join(1000) }
    }
    private fun respond(socket: Socket, code: Int, headers: String = "") {
        socket.getOutputStream().apply {
            write("HTTP/1.1 $code Fixture\r\n${headers}Content-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
            flush()
        }
    }
    private fun client() = NetExchangeGuard.configure(OkHttpClient.Builder()).followRedirects(false).build()

    @Test fun serviceUnavailableRetryAfterZeroDoesNotReplayARequest() = server({ exchange, _ ->
        respond(exchange, 503, "Retry-After: 0\r\n")
    }) { url, count ->
        val client = client()
        try {
            try {
                client.newCall(NetExchangeGuard.schedule(Request.Builder().url(url).build())).execute().close()
                fail("An internal follow-up should be blocked")
            } catch (error: NetHttpException) { assertEquals(503, error.status) }
            assertEquals(1, count.get())
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun connectionDroppedAfterReceiptDoesNotReplayCallback() = server({ _, _ ->
        // The server consumes the request then closes without headers, as if a code was consumed.
    }) { url, count ->
        val client = client()
        try {
            try {
                client.newCall(NetExchangeGuard.schedule(Request.Builder().url(url).build())).execute().close()
                fail("Expected a disconnected response")
            } catch (_: IOException) { }
            assertEquals(1, count.get())
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }

    @Test fun explicitRedirectStepsHaveIndependentBudgets() = server({ exchange, index ->
        respond(exchange, if (index == 1) 302 else 200, if (index == 1) "Location: /next\r\n" else "")
    }) { url, count ->
        val client = client()
        try {
            client.newCall(NetExchangeGuard.schedule(Request.Builder().url(url).build())).execute().use {
                assertEquals(302, it.code)
                assertEquals(1, count.get())
            }
            client.newCall(NetExchangeGuard.schedule(Request.Builder().url(url + "next").build())).execute().use { assertEquals(200, it.code) }
            assertEquals(2, count.get())
        } finally { client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown() }
    }
}
