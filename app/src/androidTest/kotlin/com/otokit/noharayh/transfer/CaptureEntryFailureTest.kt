package com.otokit.noharayh.transfer

import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.capture.LoopbackCaptureProxy
import com.otokit.noharayh.transfer.net.NetHttpException
import com.otokit.noharayh.transfer.net.NetSession
import com.otokit.noharayh.transfer.task.CaptureChannel
import com.otokit.noharayh.transfer.task.CaptureTaskCoordinator
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import java.net.Socket
import java.net.URI
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

/** On-device loopback fault telemetry. No external authorization or score upload is performed. */
@RunWith(AndroidJUnit4::class)
class CaptureEntryFailureTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun authorizationFailuresRespondFinishAndReleaseTheEntryPort() {
        val results = JSONArray()
        for (error in listOf(IOException("private-test-secret"), NetHttpException(503), NetHttpException(200))) {
            val root = File(context.cacheDir, "entry-failure-${System.nanoTime()}").apply { mkdirs() }
            val fault = AtomicReference<Exception?>(error)
            val closes = AtomicInteger()
            val http = CopyOnWriteArrayList<Int>()
            val events = CopyOnWriteArrayList<String>()
            val started = SystemClock.elapsedRealtime()
            val factory = {
                object : NetSession {
                    private val closed = AtomicBoolean(false)
                    override fun authorizationUrl(game: String): URI {
                        fault.get()?.let { throw it }
                        return URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=local-test")
                    }
                    override fun login(callback: URI, saveDiagnostic: (NetSession.Page) -> Unit) = error("Unexpected login")
                    override fun <T> read(uri: URI, post: String?, consume: (NetSession.Page) -> T): T = error("Unexpected download")
                    override fun close() { if (closed.compareAndSet(false, true)) closes.incrementAndGet() }
                }
            }
            CaptureTaskCoordinator(FileHtmlCaptures(root), ::openChannel, factory, 0, 0,
                startRecording = { _, _ -> TransferRecording(
                    { stage, _, _ -> events.add(stage) },
                    { okhttp3.Interceptor { chain -> chain.proceed(chain.request()) } },
                    {}, {}, { http.add(it.status) }) }).use { tasks ->
                val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "device-entry-failure")
                assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request))
                await { tasks.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
                val response = openEntry()
                await { tasks.snapshot()?.terminal == true && events.contains("CLEANUP") }
                val failed = tasks.snapshot()!!
                assertTrue(response.startsWith("HTTP/1.1 502"))
                assertTrue(response.contains("请返回 OTOKiT 重新开始。</p>"))
                assertTrue(response.trimEnd().endsWith("</html>"))
                assertFalse(response.contains("private-test-secret"))
                val bytes = response.substringAfter("Content-Length: ").substringBefore("\r\n").toInt()
                assertEquals(bytes, response.substringAfter("\r\n\r\n").toByteArray(Charsets.UTF_8).size)
                assertEquals(CaptureStage.FAILED, failed.stage)
                assertEquals(CaptureFailure.NETWORK, failed.failure)
                assertEquals((error as? NetHttpException)?.status, failed.httpStatus)
                assertNull(failed.entryUrl)
                assertNull(failed.proxyPort)
                assertTrue(failed.pages.isEmpty())
                assertEquals(listOf(502), http.toList())
                assertTrue(events.contains("AUTHORIZATION"))
                assertFalse(events.contains("CALLBACK"))
                assertEquals(1, closes.get())
                val elapsed = SystemClock.elapsedRealtime() - started

                // Binding the same device port again proves the previous channel released it.
                fault.set(null)
                assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request.copy(launchId = "device-entry-restart")))
                await { tasks.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
                assertTrue(openEntry().startsWith("HTTP/1.1 302"))
                tasks.cancel()
                await { tasks.snapshot()?.terminal == true }
                assertEquals(CaptureStage.CANCELLED, tasks.snapshot()!!.stage)
                assertEquals(2, closes.get())
                results.put(JSONObject().put("error", error.javaClass.simpleName)
                    .put("upstreamStatus", (error as? NetHttpException)?.status ?: JSONObject.NULL)
                    .put("entryStatus", 502).put("terminal", failed.stage.name)
                    .put("elapsedMs", elapsed).put("restartEntryStatus", 302).put("sessionCloses", closes.get()))
            }
            root.deleteRecursively()
        }
        val report = File(context.filesDir, "transfer-entry-tests/fault-results.json").apply { parentFile!!.mkdirs() }
        report.writeText(JSONObject().put("cases", results).toString(2))
        instrumentation.sendStatus(2, Bundle().apply { putString("entryFaultTelemetry", results.toString()) })
    }

    @Test fun cancellingAnAuthorizationRequestKeepsCancelledTerminal() {
        val root = File(context.cacheDir, "entry-cancel-${System.nanoTime()}").apply { mkdirs() }
        val entered = CountDownLatch(1)
        val released = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val client = Executors.newSingleThreadExecutor()
        val session = object : NetSession {
            override fun authorizationUrl(game: String): URI {
                entered.countDown()
                try { assertTrue(released.await(5, TimeUnit.SECONDS)) }
                finally { finished.countDown() }
                throw IOException("Cancelled authorization")
            }
            override fun login(callback: URI, saveDiagnostic: (NetSession.Page) -> Unit) = error("Unexpected login")
            override fun <T> read(uri: URI, post: String?, consume: (NetSession.Page) -> T): T = error("Unexpected download")
            override fun close() { released.countDown() }
        }
        try {
            CaptureTaskCoordinator(FileHtmlCaptures(root), ::openChannel, { session }, 0, 0).use { tasks ->
                tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "device-cancel-entry"))
                await { tasks.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
                val request = client.submit { try { openEntry() } catch (_: IOException) { } }
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                tasks.cancel()
                await { tasks.snapshot()?.terminal == true }
                assertTrue(finished.await(3, TimeUnit.SECONDS))
                request.get(3, TimeUnit.SECONDS)
                assertEquals(CaptureStage.CANCELLED, tasks.snapshot()!!.stage)
                assertNull(tasks.snapshot()!!.entryUrl)
                assertNull(tasks.snapshot()!!.proxyPort)
            }
        } finally { released.countDown(); client.shutdownNow(); root.deleteRecursively() }
    }

    private fun openChannel(network: com.otokit.noharayh.network.NetworkSnapshot, authorize: () -> URI, callback: (Result<URI>) -> Unit, event: (String) -> Unit): CaptureChannel {
        val proxy = LoopbackCaptureProxy("/entry", onCallback = callback, onEvent = event,
            authorizationFactory = authorize, callbackHtml = "Unused callback page",
            authorizationErrorHtml = context.assets.open("transfer/wechat-entry-error.html").bufferedReader().use { it.readText() })
        proxy.start()
        return object : CaptureChannel {
            override val entryUrl = "http://127.0.0.2:8284/entry"
            override val proxyPort = proxy.port
            override fun observeHttp(observer: (CaptureLocalHttpExchange) -> Unit) = proxy.observeHttp(observer)
            override fun close() = proxy.close()
        }
    }

    private fun openEntry(): String = Socket("127.0.0.2", 8284).use { socket ->
        socket.soTimeout = 3000
        socket.getOutputStream().write("GET /entry HTTP/1.1\r\nHost: 127.0.0.2:8284\r\n\r\n".toByteArray())
        socket.getInputStream().readBytes().toString(Charsets.UTF_8)
    }

    private fun await(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 5000
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < deadline) { "Task did not reach expected state" }
            SystemClock.sleep(10)
        }
    }
}
