package com.otokit.noharayh.network

import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.Closeable
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLException

enum class NetworkCheckStage { ENTRY_BIND, PROXY_BIND, UPSTREAM, TARGET }
enum class NetworkCheckStatus { PASSED, FAILED, SKIPPED }
enum class NetworkCheckReason { PORT_IN_USE, INVALID_ROUTE, SELF_ROUTE, PROXY_AUTHENTICATION, PROXY_REJECTED, CONNECT_FAILED, TLS_FAILED, TARGET_HTTP, CANCELLED }
data class NetworkCheckStep(val stage: NetworkCheckStage, val status: NetworkCheckStatus, val durationMs: Long,
                            val reason: NetworkCheckReason? = null, val httpStatus: Int? = null)
data class NetworkCheckReport(val revision: Long, val scope: ProxyScope, val steps: List<NetworkCheckStep>, val cancelled: Boolean,
                              val targetUrl: String, val startedAtMs: Long)

/** 只测绑定、代理握手和无凭据 HEAD，不启动采集/VPN，也不替代业务成功判定。回调在检查线程。 */
class NetworkChannelCheck(private val client: OkHttpClient = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
    .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()) {
    fun start(snapshot: NetworkSnapshot, scope: ProxyScope, targetUrl: String,
              onStep: (NetworkCheckStep) -> Unit, onComplete: (NetworkCheckReport) -> Unit): Closeable {
        val cancelled = AtomicBoolean(false)
        val startedAtMs = System.currentTimeMillis()
        val resources = ConcurrentHashMap.newKeySet<Closeable>()
        val calls = ConcurrentHashMap.newKeySet<Call>()
        val thread = Thread({
            val steps = mutableListOf<NetworkCheckStep>()
            fun publish(step: NetworkCheckStep) { steps += step; onStep(step) }
            fun active() { if (cancelled.get()) throw CheckCancelled() }
            fun <T : Closeable> register(value: T): T {
                resources += value
                if (cancelled.get()) value.close()
                return value
            }
            fun step(stage: NetworkCheckStage, action: () -> Int?): Boolean {
                val started = System.nanoTime()
                return try {
                    active()
                    val status = action()
                    active()
                    publish(NetworkCheckStep(stage, NetworkCheckStatus.PASSED, (System.nanoTime() - started) / 1_000_000,
                        httpStatus = status)); true
                } catch (failure: IOException) {
                    val reason = when {
                        cancelled.get() -> NetworkCheckReason.CANCELLED
                        failure is NetworkRouteException -> failure.reason
                        failure is BindException -> NetworkCheckReason.PORT_IN_USE
                        failure is SSLException -> NetworkCheckReason.TLS_FAILED
                        else -> NetworkCheckReason.CONNECT_FAILED
                    }
                    publish(NetworkCheckStep(stage, NetworkCheckStatus.FAILED, (System.nanoTime() - started) / 1_000_000,
                        reason, (failure as? NetworkRouteException)?.httpStatus)); false
                }
            }
            try {
                val target = try { URI(targetUrl).also {
                    require(it.scheme.equals("https", true) && it.host != null && it.rawUserInfo == null && it.rawQuery == null &&
                        it.rawFragment == null && it.port in -1..65535 && it.port != 0)
                } } catch (_: IllegalArgumentException) {
                    publish(NetworkCheckStep(NetworkCheckStage.TARGET, NetworkCheckStatus.FAILED, 0, NetworkCheckReason.INVALID_ROUTE))
                    return@Thread
                } catch (_: java.net.URISyntaxException) {
                    publish(NetworkCheckStep(NetworkCheckStage.TARGET, NetworkCheckStatus.FAILED, 0, NetworkCheckReason.INVALID_ROUTE))
                    return@Thread
                }
                val entry = register(ServerSocket())
                val entryOk = step(NetworkCheckStage.ENTRY_BIND) { entry.bind(InetSocketAddress(snapshot.entryHost, snapshot.entryPort)); null }
                val proxy = register(ServerSocket())
                val proxyOk = step(NetworkCheckStage.PROXY_BIND) { proxy.bind(InetSocketAddress(snapshot.proxyHost, snapshot.proxyPort)); null }
                val listeners = listOfNotNull(entry.localSocketAddress as? InetSocketAddress, proxy.localSocketAddress as? InetSocketAddress)
                entry.close(); proxy.close()
                if (!entryOk || !proxyOk || cancelled.get()) return@Thread
                if (NetworkRoute.usesUpstream(snapshot, scope)) {
                    if (!step(NetworkCheckStage.UPSTREAM) {
                        NetworkRoute.rejectSelfRoute(snapshot, scope, listeners)
                        NetworkRoute.openSocket(snapshot, scope, target.host, if (target.port < 0) 443 else target.port,
                            12_000, { register(it) }).use { }
                        200
                    }) return@Thread
                } else publish(NetworkCheckStep(NetworkCheckStage.UPSTREAM, NetworkCheckStatus.SKIPPED, 0))
                step(NetworkCheckStage.TARGET) {
                    active()
                    val call = NetworkRoute.client(client, snapshot, scope).newCall(Request.Builder().url(targetUrl).head().build())
                    calls += call
                    if (cancelled.get()) call.cancel()
                    val status = call.execute().use { it.code }
                    if (status >= 400) throw NetworkRouteException(NetworkCheckReason.TARGET_HTTP, status)
                    status
                }
            } finally {
                calls.forEach { it.cancel() }
                resources.forEach { try { it.close() } catch (_: IOException) { } }
                onComplete(NetworkCheckReport(snapshot.revision, scope, Collections.unmodifiableList(steps.toList()), cancelled.get(),
                    targetUrl, startedAtMs))
            }
        }, "ProxyChannelCheck")
        thread.start()
        return Closeable {
            cancelled.set(true)
            calls.forEach { it.cancel() }
            resources.forEach { try { it.close() } catch (_: IOException) { } }
            thread.interrupt()
        }
    }

    private class CheckCancelled : IOException()
}
