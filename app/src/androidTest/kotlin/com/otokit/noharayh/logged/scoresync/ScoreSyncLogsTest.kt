package com.otokit.noharayh.logged.scoresync

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.score.html.HtmlExportRedactor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.net.ServerSocket
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipInputStream

@RunWith(AndroidJUnit4::class)
class ScoreSyncLogsTest {
    @Test fun exportingOneTaskDoesNotBlockStartingAnotherButKeepsItsOwnWriterLock() = fixture { root ->
        val logs = ScoreSyncLogs(root, "https://example.test/repository", "test")
        val original = logs.start("task-exporting", "maimai", NOW)!!
        original.finish("COMPLETED")
        val ref = logs.list().single().ref
        val writing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val first = AtomicBoolean(true)
        val pool = Executors.newFixedThreadPool(3)
        val output = object : ByteArrayOutputStream() {
            override fun write(buffer: ByteArray, offset: Int, length: Int) {
                if (first.compareAndSet(true, false)) {
                    writing.countDown()
                    check(release.await(5, TimeUnit.SECONDS)) { "Export fixture was not released" }
                }
                super.write(buffer, offset, length)
            }
        }
        try {
            val exporting = pool.submit<Boolean> {
                var success = false
                logs.export(setOf(ref), false, false, { write -> write(output) }) {
                    assertFalse(Thread.holdsLock(logs))
                    assertFalse(Thread.holdsLock(original))
                    success = it
                }
                success
            }
            assertTrue(writing.await(3, TimeUnit.SECONDS))
            val retainStarted = CountDownLatch(1)
            val sameTask = pool.submit<Boolean> {
                retainStarted.countDown()
                original.retain().close()
                true
            }
            assertTrue(retainStarted.await(3, TimeUnit.SECONDS))
            val nextTask = pool.submit<ScoreSyncLogSession?> { logs.start("task-new", "chunithm", NOW) }
            val next = nextTask.get(2, TimeUnit.SECONDS)
            assertNotNull("A different task must start while the ZIP output is blocked", next)
            next!!.finish("COMPLETED")
            try {
                sameTask.get(150, TimeUnit.MILLISECONDS)
                fail("The exported task accepted a new writer before the export released its lock")
            } catch (_: TimeoutException) { /* 仅所选记录继续排斥恢复追加。 */ }
            release.countDown()
            assertTrue(exporting.get(3, TimeUnit.SECONDS))
            assertTrue(sameTask.get(3, TimeUnit.SECONDS))
        } finally {
            release.countDown()
            pool.shutdownNow()
            assertTrue(pool.awaitTermination(3, TimeUnit.SECONDS))
        }
    }

    @Test fun pendingScoreWorkSealsOnlyAfterReleaseAndExportUsesBatchTimeAndAttachments() = fixture { root ->
        val html = "<div class='name_block'>private-player</div><div class='music_name_block'>Chart</div>"
        val logs = ScoreSyncLogs(root, "https://example.test/repository", "test", { game, batch ->
            assertEquals("maimai", game); assertEquals("batch-one", batch)
            listOf(ScoreSyncLogAttachment("MaiRecord_HTML_20261120102030_Master.html") { html.byteInputStream() })
        }, HtmlExportRedactor::write)
        val session = logs.start("task-one", "maimai", NOW, mapOf("token" to "configuration-secret"))!!
        session.bindBatch("batch-one", "20261120102030")
        val work = session.retain()
        session.event("LOCAL_PROCESSING", "正在生成 JSON", mapOf("friendCode" to "123456789012"))
        session.finish("COMPLETED")
        assertTrue(logs.list().single().writing)
        assertFalse(export(logs, redact = true, includeHtml = true).first)
        assertNull("An active-log export rejection is not a recording failure", logs.recordingError)
        work.close(); work.close()
        assertFalse(logs.list().single().writing)
        val (success, entries) = export(logs, redact = true, includeHtml = true)
        assertTrue(success)
        val raw = export(logs, redact = false, includeHtml = true)
        assertTrue(raw.first)
        assertTaskLayout(entries, "maimai/task-one/")
        assertTaskLayout(raw.second, "maimai/task-one/")
        assertEquals(raw.second.keys, entries.keys)
        val main = entries.entries.single { it.key.endsWith("MaiRecord_Log_20261120102030.jsonl") }.value.toString(Charsets.UTF_8)
        val header = JSONObject(main.lineSequence().first(String::isNotEmpty))
        assertEquals("HEADER", header.getString("stage"))
        assertEquals("Repository header message", "仓库：https://example.test/repository",
            header.getString("message").substringAfter("] "))
        main.lineSequence().filter(String::isNotEmpty).forEach { line ->
            val event = JSONObject(line)
            assertTrue(event.getString("message").matches(Regex("\\[[0-9]{6}] .*")))
            assertFalse(line.contains("configuration-secret"))
            assertFalse(line.contains("123456789012"))
        }
        val htmlPath = "maimai/task-one/html/MaiRecord_HTML_20261120102030_Master.html"
        assertEquals(listOf(htmlPath), entries.keys.filter { it.contains("/html/") })
        assertArrayEquals(html.toByteArray(Charsets.UTF_8), raw.second.getValue(htmlPath))
        val exportedHtml = entries.getValue(htmlPath).toString(Charsets.UTF_8)
        assertFalse(exportedHtml.contains("private-player"))
        assertTrue(exportedHtml.contains("Chart"))
        assertTrue(html.contains("private-player"))
        assertTrue(logs.preview(logs.list().single().ref).text.contains("[REDACTED]"))
    }

    @Test fun oneShotRequestAndUnreadCompressedErrorResponseAreCapturedWithoutReplay() = fixture { root ->
        val logs = ScoreSyncLogs(root, "https://example.test/repository", "test", redactHtml = HtmlExportRedactor::write)
        val session = logs.start("task-http", "chunithm", NOW)!!
        val responseText = """{"access_token":"response-secret","success":false,"status":400}"""
        val compressed = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(responseText.toByteArray()) } }.toByteArray()
        val requestText = "token=request-secret&genre=99"
        val writes = AtomicInteger()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val received = CompletableFuture<String>()
        val serving = Thread {
            try {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream()
                    val header = ByteArrayOutputStream()
                    while (!header.toString("UTF-8").endsWith("\r\n\r\n")) header.write(input.read().also { check(it >= 0) })
                    val length = Regex("(?im)^Content-Length: ([0-9]+)").find(header.toString("UTF-8"))!!.groupValues[1].toInt()
                    val bytes = ByteArray(length)
                    var at = 0
                    while (at < length) at += input.read(bytes, at, length - at).also { check(it > 0) }
                    received.complete(bytes.toString(Charsets.UTF_8))
                    socket.getOutputStream().apply {
                        write(("HTTP/1.1 400 Bad Request\r\nContent-Type: application/json\r\nContent-Encoding: gzip\r\n" +
                            "Content-Length: ${compressed.size}\r\nConnection: close\r\n\r\n").toByteArray())
                        write(compressed); flush()
                    }
                }
            } catch (error: Exception) { received.completeExceptionally(error) }
        }.apply { start() }
        val client = OkHttpClient.Builder().proxy(Proxy.NO_PROXY).retryOnConnectionFailure(false)
            .addNetworkInterceptor(session.httpInterceptor).build()
        try {
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/error?code=oauth-secret")
                .header("Authorization", "Bearer authorization-secret").post(object : RequestBody() {
                    override fun contentType() = "application/x-www-form-urlencoded".toMediaType()
                    override fun contentLength() = requestText.toByteArray().size.toLong()
                    override fun isOneShot() = true
                    override fun writeTo(sink: BufferedSink) { writes.incrementAndGet(); sink.writeUtf8(requestText) }
                }).build()
            client.newCall(request).execute().use { response ->
                assertEquals(400, response.code)
                assertEquals("{", response.body!!.source().readUtf8(1))
                // use.close 必须在相同调用内读取剩余响应，不能重复请求。
            }
            assertEquals(requestText, received.get(5, TimeUnit.SECONDS))
            assertEquals(1, writes.get())
            session.finish("FAILED")
            assertFalse(logs.list().single().writing)
            val (rawSuccess, raw) = export(logs, redact = false)
            assertTrue(rawSuccess)
            assertTaskLayout(raw, "chunithm/task-http/")
            assertArrayEquals(compressed, raw.entries.single { it.key.endsWith("_ResponseBody.body") }.value)
            val metadata = JSONObject(raw.entries.single { it.key.endsWith("_Request.json") }.value.toString(Charsets.UTF_8))
            assertEquals("COMPLETE", metadata.getJSONObject("responseBody").getString("state"))
            assertEquals("COMPLETE", metadata.getJSONObject("requestBody").getString("state"))
            val (success, clean) = export(logs, redact = true)
            assertTrue(success)
            assertTaskLayout(clean, "chunithm/task-http/")
            assertEquals(raw.keys, clean.keys)
            val allText = clean.values.joinToString("\n") { it.toString(Charsets.UTF_8) }
            listOf("oauth-secret", "authorization-secret", "request-secret", "response-secret").forEach {
                assertFalse("Leaked $it", allText.contains(it))
            }
            assertTrue(allText.contains("genre=99"))
            val cleanedMetadata = JSONObject(clean.entries.single { it.key.endsWith("_Request.json") }.value.toString(Charsets.UTF_8))
            assertEquals("", cleanedMetadata.getJSONObject("responseBody").getString("contentEncoding"))
            assertEquals("gzip", cleanedMetadata.getJSONObject("responseBody").getString("originalContentEncoding"))
        } finally {
            server.close(); serving.join(5000)
            client.connectionPool.evictAll(); client.dispatcher.executorService.shutdown()
        }
    }

    @Test fun restartKeepsCompleteLinesAndSealsEvenWhenRequestMetadataWasInterrupted() = fixture { root ->
        val logs = ScoreSyncLogs(root, "https://example.test/repository", "test")
        val session = logs.start("task-recovery", "maimai", NOW)!!
        session.localHttp("CALLBACK", "GET", "http://localhost/health", emptyList(), 200,
            listOf("Content-Type" to "text/plain"), "ready".toByteArray())
        val directory = File(root, "maimai/task-recovery")
        directory.listFiles()!!.single { it.extension == "jsonl" }.appendText("{\"unfinished\":")
        directory.listFiles()!!.single { it.name.endsWith("_Request.json") }.writeText("{")
        val restored = ScoreSyncLogs(root, "https://example.test/repository", "test")
        val summary = restored.list().single()
        assertFalse(summary.writing)
        assertEquals("INTERRUPTED", summary.status)
        assertEquals("InterruptedRequestMetadata", summary.recordingError)
        val events = directory.listFiles()!!.single { it.extension == "jsonl" }.readLines()
        assertTrue(events.isNotEmpty())
        events.forEach { JSONObject(it) }
        val pending = restored.find("task-recovery")!!.retain()
        assertTrue(restored.list().single().writing)
        pending.close()
        assertFalse(restored.list().single().writing)
        assertEquals("INTERRUPTED", restored.list().single().status)
        val raw = export(restored, redact = false)
        assertTrue(raw.first)
        assertEquals("{", raw.second.entries.single { it.key.endsWith("_Request.json") }.value.toString(Charsets.UTF_8))
        assertFalse(export(restored, redact = true).first)
        assertEquals(setOf(summary.ref), restored.remove(setOf(summary.ref)).removed)
    }

    @Test fun recordingFailureDoesNotEscapeAndUnknownBodiesRequireRawExport() = fixture { root ->
        val logs = ScoreSyncLogs(root, "https://example.test/repository", "test")
        val session = logs.start("task-failure", "maimai", NOW)!!
        val directory = File(root, "maimai/task-failure")
        directory.listFiles()!!.single { it.extension == "jsonl" }.apply { assertTrue(delete()); assertTrue(mkdir()) }
        session.event("FETCHING", "此文件故意不可写")
        session.finish("FAILED")
        val failed = logs.list().single()
        assertFalse(failed.writing)
        assertNotNull(failed.recordingError)
        assertEquals(setOf(failed.ref), logs.remove(setOf(failed.ref)).removed)

        val next = logs.start("task-unknown", "chunithm", NOW)!!
        next.localHttp("CALLBACK", "GET", "http://localhost/unknown", emptyList(), 200,
            listOf("Content-Type" to "application/octet-stream"), byteArrayOf(1, 2, 3))
        next.finish("COMPLETED")
        assertFalse(export(logs, redact = true).first)
        assertNull("Unsupported redaction must not mark successful recording as failed", logs.recordingError)
        assertTrue(export(logs, redact = false).first)
    }

    private fun assertTaskLayout(entries: Map<String, ByteArray>, taskPath: String) {
        assertTrue(entries.keys.all { it.startsWith(taskPath) })
        val topLevel = entries.keys.filter { !it.removePrefix(taskPath).contains('/') }.toSet()
        val main = topLevel.single { it.endsWith(".jsonl") }
        assertEquals(setOf(main, main.removeSuffix(".jsonl") + "_Index.json"), topLevel)
        val requestFiles = entries.keys.filter { it.endsWith("_Request.json") || it.endsWith(".body") }
        requestFiles.forEach { path ->
            assertTrue("Request metadata and bodies belong in requests/: $path", path.startsWith(taskPath + "requests/"))
            assertFalse(path.removePrefix(taskPath + "requests/").contains('/'))
        }
        val referencedBodies = linkedSetOf<String>()
        requestFiles.filter { it.endsWith("_Request.json") }.forEach { path ->
            val metadata = JSONObject(entries.getValue(path).toString(Charsets.UTF_8))
            listOf("requestBody", "responseBody").forEach { key ->
                metadata.optJSONObject(key)?.takeIf { it.has("file") }?.let { body ->
                    val name = body.getString("file")
                    assertFalse("Body references must remain relative to their metadata directory", name.contains('/'))
                    assertFalse(name.contains('\\'))
                    val bodyPath = path.substringBeforeLast('/') + "/" + name
                    assertTrue("Missing body referenced by $path", entries.containsKey(bodyPath))
                    assertEquals(body.getLong("bytes"), entries.getValue(bodyPath).size.toLong())
                    referencedBodies += bodyPath
                }
            }
        }
        assertEquals(requestFiles.filter { it.endsWith(".body") }.toSet(), referencedBodies)
    }

    private fun export(logs: ScoreSyncLogs, redact: Boolean, includeHtml: Boolean = false): Pair<Boolean, Map<String, ByteArray>> {
        val output = ByteArrayOutputStream()
        var success = false
        logs.export(logs.list().map { it.ref }.toSet(), redact, includeHtml, { write -> write(output) }) { success = it }
        if (!success) return false to emptyMap()
        val entries = ZipInputStream(output.toByteArray().inputStream()).use { zip -> buildMap {
            while (true) {
                val entry = zip.nextEntry ?: break
                put(entry.name, zip.readBytes())
            }
        } }
        return true to entries
    }

    private fun fixture(run: (File) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "sync-log-test-").toFile()
        try { run(root) } finally { assertTrue(root.deleteRecursively()) }
    }

    private companion object { val NOW: Instant = Instant.parse("2026-11-20T02:20:30Z") }
}
