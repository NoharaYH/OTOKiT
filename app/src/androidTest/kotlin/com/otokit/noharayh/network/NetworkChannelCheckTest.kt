package com.otokit.noharayh.network

import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class NetworkChannelCheckTest {
    @Test fun scopesControlTheActualRouteAndTargetHttpFailureKeepsItsOwnStage() {
        LocalHttpsFixture { LocalHttpsFixture.Response(403) }.use { target ->
            LocalProxyFixture("fixture-user" to "fixture-password").use { proxy ->
                val snapshot = NetworkSnapshot(42, proxy.upstream(), setOf(ProxyScope.NET), entryPort = 0, proxyPort = 0)
                val startedAt = System.currentTimeMillis()
                val direct = check(target.client, snapshot, ProxyScope.PLATFORM, target.url())
                assertEquals(42L, direct.revision)
                assertEquals(target.url(), direct.targetUrl)
                assertTrue(direct.startedAtMs in startedAt..System.currentTimeMillis())
                assertEquals(ProxyScope.PLATFORM, direct.scope)
                assertEquals(NetworkCheckStatus.SKIPPED, direct.steps.single { it.stage == NetworkCheckStage.UPSTREAM }.status)
                assertEquals(NetworkCheckReason.TARGET_HTTP, direct.steps.last().reason)
                assertEquals(NetworkCheckStage.TARGET, direct.steps.last().stage)
                assertEquals(403, direct.steps.last().httpStatus)
                assertTrue(proxy.requests.isEmpty())
                val throughProxy = check(target.client, snapshot, ProxyScope.NET, target.url())
                assertEquals(NetworkCheckStatus.PASSED, throughProxy.steps.single { it.stage == NetworkCheckStage.UPSTREAM }.status)
                assertEquals(NetworkCheckReason.TARGET_HTTP, throughProxy.steps.last().reason)
                assertTrue(proxy.requests.isNotEmpty())
                assertTrue(proxy.requests.all { it.method == "CONNECT" })
                assertEquals(listOf("HEAD", "HEAD"), target.requests.map { it.method })
            }
        }
    }

    @Test fun occupiedBindingAndRejectedProxyAuthenticationAreNotReportedAsAWorkingChannel() {
        LocalHttpsFixture { LocalHttpsFixture.Response() }.use { target ->
            ServerSocket().apply { bind(InetSocketAddress("127.0.0.2", 0)) }.use { occupied ->
                val report = check(target.client, NetworkSnapshot(entryPort = occupied.localPort, proxyPort = 0), ProxyScope.NET, target.url())
                val entry = report.steps.single { it.stage == NetworkCheckStage.ENTRY_BIND }
                assertEquals(NetworkCheckStatus.FAILED, entry.status)
                assertEquals(NetworkCheckReason.PORT_IN_USE, entry.reason)
                assertFalse(report.steps.any { it.stage == NetworkCheckStage.TARGET })
                assertTrue(target.requests.isEmpty())
            }
            LocalProxyFixture("fixture-user" to "correct-password").use { proxy ->
                val snapshot = NetworkSnapshot(upstream = UpstreamProxy("127.0.0.1", proxy.port, "fixture-user", "wrong-password"),
                    scopes = setOf(ProxyScope.NET), entryPort = 0, proxyPort = 0)
                val report = check(target.client, snapshot, ProxyScope.NET, target.url())
                assertEquals(NetworkCheckStage.UPSTREAM, report.steps.last().stage)
                assertEquals(NetworkCheckReason.PROXY_AUTHENTICATION, report.steps.last().reason)
                assertEquals(407, report.steps.last().httpStatus)
                assertEquals(2, proxy.requests.size)
                assertTrue(target.requests.isEmpty())
            }
        }
    }

    @Test fun cancellingAnUnresponsiveProxyClosesTheInFlightSocketAndReportsCancellation() {
        val server = ServerSocket().apply { bind(InetSocketAddress("127.0.0.1", 0)) }
        val accepted = CountDownLatch(1)
        val end = CountDownLatch(1)
        val stopped = CompletableFuture<NetworkCheckReport>()
        var peer: Socket? = null
        val thread = Thread {
            try {
                server.accept().use { socket ->
                    peer = socket
                    LocalHttpsFixture.readHeader(socket.getInputStream())
                    accepted.countDown()
                    end.await(5, TimeUnit.SECONDS)
                }
            } catch (_: java.io.IOException) { }
        }.apply { start() }
        val snapshot = NetworkSnapshot(73, UpstreamProxy("127.0.0.1", server.localPort), setOf(ProxyScope.NET), entryPort = 0, proxyPort = 0)
        val checking = NetworkChannelCheck().start(snapshot, ProxyScope.NET, "https://127.0.0.1:8443/", {}, stopped::complete)
        try {
            assertTrue(accepted.await(3, TimeUnit.SECONDS))
            checking.close()
            val report = stopped.get(3, TimeUnit.SECONDS)
            assertEquals(73L, report.revision)
            assertTrue(report.cancelled)
            assertEquals(NetworkCheckReason.CANCELLED, report.steps.last().reason)
            assertEquals(NetworkCheckStage.UPSTREAM, report.steps.last().stage)
        } finally {
            checking.close(); end.countDown(); peer?.close(); server.close(); thread.join(3000)
            assertFalse(thread.isAlive)
        }
    }

    @Test fun invalidTargetAndProvenSelfRouteAreRejectedWithoutContactingTheTarget() {
        LocalHttpsFixture { LocalHttpsFixture.Response() }.use { target ->
            val malformed = check(target.client, NetworkSnapshot(entryPort = 0, proxyPort = 0), ProxyScope.NET, "https://name:secret@example.test/")
            assertEquals(NetworkCheckReason.INVALID_ROUTE, malformed.steps.single().reason)
            val port = ServerSocket().use { it.bind(InetSocketAddress("127.0.0.2", 0)); it.localPort }
            val self = NetworkSnapshot(upstream = UpstreamProxy("127.0.0.2", port), scopes = setOf(ProxyScope.NET), entryPort = port, proxyPort = 0)
            val report = check(target.client, self, ProxyScope.NET, target.url())
            assertEquals(NetworkCheckStage.UPSTREAM, report.steps.last().stage)
            assertEquals(NetworkCheckReason.SELF_ROUTE, report.steps.last().reason)
            assertTrue(target.requests.isEmpty())
        }
    }

    private fun check(client: okhttp3.OkHttpClient, snapshot: NetworkSnapshot, scope: ProxyScope, target: String): NetworkCheckReport {
        val completed = CompletableFuture<NetworkCheckReport>()
        val steps = mutableListOf<NetworkCheckStep>()
        val running = NetworkChannelCheck(client).start(snapshot, scope, target, { steps += it }, completed::complete)
        return try {
            completed.get(10, TimeUnit.SECONDS).also { assertEquals(steps, it.steps); assertFalse(it.cancelled) }
        } finally { running.close() }
    }
}
