package com.otokit.noharayh.logged.scoresync

import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.ForwardingSource
import okio.buffer
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import com.otokit.noharayh.storage.files.writeAtomically

/** Network interceptor：记录原来的单次收发，不重读一次性请求体。 */
internal class ScoreSyncHttpCapture(private val session: ScoreSyncLogSession, private val stage: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val record = session.beginHttp(stage, original.method, original.url.toString(), original.headers.toList())
        val request = original.newBuilder().apply {
            original.body?.let { delegate -> method(original.method, object : RequestBody() {
                override fun contentType() = delegate.contentType()
                override fun contentLength() = delegate.contentLength()
                override fun isOneShot() = delegate.isOneShot()
                override fun isDuplex() = delegate.isDuplex()
                override fun writeTo(sink: BufferedSink) {
                    val body = record.body(true, contentType()?.toString().orEmpty(), original.header("Content-Encoding").orEmpty())
                    val tee = object : ForwardingSink(sink) {
                        override fun write(source: Buffer, byteCount: Long) {
                            body.copy(source, 0, byteCount)
                            super.write(source, byteCount)
                        }
                    }.buffer()
                    try { delegate.writeTo(tee); tee.flush(); body.complete() }
                    catch (error: IOException) { body.partial(error); throw error }
                }
            }) }
        }.build()
        try {
            val response = chain.proceed(request)
            record.response(response.code, response.protocol.toString(), response.headers.toList())
            val originalBody = response.body
            if (originalBody == null) { record.close(); return response }
            val body = record.body(false, originalBody.contentType()?.toString().orEmpty(), response.header("Content-Encoding").orEmpty())
            val source = object : ForwardingSource(originalBody.source()) {
                private var eof = false
                private var failed = false
                private var closed = false
                override fun read(sink: Buffer, byteCount: Long): Long = try {
                    val start = sink.size
                    super.read(sink, byteCount).also { count ->
                        if (count == -1L) { eof = true; body.complete() }
                        else if (count > 0) body.copy(sink, start, count)
                    }
                } catch (error: IOException) { failed = true; body.partial(error); throw error }

                override fun close() {
                    if (closed) return
                    closed = true
                    // 此时业务仍持有同一个 active Call。附加读取的失败不覆盖已有业务结果。
                    if (!eof && !failed && session.canRecord()) {
                        val discard = Buffer()
                        try { while (session.canRecord() && read(discard, 8192) != -1L) discard.clear() }
                        catch (_: IOException) { /* read 已把真实部分正文与失败原因落入记录。 */ }
                    }
                    if (!eof) body.stopped()
                    try { super.close() } finally { record.close() }
                }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = originalBody.contentType()
                override fun contentLength() = originalBody.contentLength()
                override fun source() = source
            }).build()
        } catch (error: IOException) {
            record.failure(error)
            record.close()
            throw error
        } catch (error: RuntimeException) {
            record.failure(error)
            record.close()
            throw error
        }
    }
}

internal class ScoreSyncHttpRecord(private val session: ScoreSyncLogSession, private val stage: String,
                                   number: Int, method: String, url: String, headers: List<Pair<String, String>>) : Closeable {
    private val lease = session.retain()
    private val closed = AtomicBoolean(false)
    private val started = System.nanoTime()
    private val base = ScoreSyncLogSession.prefix(session.index) + "_Request_" + number.toString().padStart(6, '0')
    private val metadataFile = File(session.directory, base + "_Request.json")
    private val metadata = JSONObject().put("requestId", number).put("stage", stage).put("method", method)
        .put("url", url).put("requestTime", session.time()).put("requestHeaders", headers(headers))
        .put("requestBody", JSONObject().put("state", "NONE")).put("responseBody", JSONObject().put("state", "NOT_RECORDED"))

    init { save(); session.event(stage, "开始 HTTP 请求", mapOf("requestId" to number.toString(), "method" to method, "url" to url)) }

    @Synchronized fun body(request: Boolean, type: String, encoding: String): ScoreSyncLogBody {
        val key = if (request) "requestBody" else "responseBody"
        val descriptor = JSONObject().put("file", base + if (request) "_RequestBody.body" else "_ResponseBody.body")
            .put("contentType", type).put("contentEncoding", encoding).put("state", "WRITING").put("bytes", 0)
        metadata.put(key, descriptor)
        save()
        return ScoreSyncLogBody(session, File(session.directory, descriptor.getString("file")), descriptor, ::save)
    }

    @Synchronized fun response(status: Int, protocol: String, values: List<Pair<String, String>>) {
        metadata.put("status", status).put("protocol", protocol).put("responseTime", session.time())
            .put("responseHeaders", headers(values))
        save()
    }

    @Synchronized fun failure(error: Exception) {
        metadata.put("error", error.javaClass.simpleName)
        save()
    }

    @Synchronized private fun save() = session.io {
        metadataFile.writeAtomically { it.write(metadata.toString().toByteArray(Charsets.UTF_8)) }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(this) {
            metadata.put("durationMs", (System.nanoTime() - started) / 1_000_000)
            save()
            session.event(stage, "结束 HTTP 请求", mapOf("requestId" to metadata.getInt("requestId").toString(),
                "status" to metadata.optInt("status").toString(), "durationMs" to metadata.getLong("durationMs").toString(),
                "bodyState" to metadata.getJSONObject("responseBody").getString("state")))
        }
        lease.close()
    }

    private fun headers(values: List<Pair<String, String>>) = JSONArray().apply {
        values.forEach { (name, value) -> put(JSONObject().put("name", name).put("value", value)) }
    }
}

internal class ScoreSyncLogBody(private val session: ScoreSyncLogSession, file: File,
                                private val descriptor: JSONObject, private val changed: () -> Unit) {
    private var output: FileOutputStream? = null
    private var finished = false
    init { session.io { output = FileOutputStream(file) } }

    @Synchronized fun write(bytes: ByteArray) = session.io {
        output?.write(bytes)
        descriptor.put("bytes", descriptor.getLong("bytes") + bytes.size)
    }

    fun copy(source: Buffer, offset: Long, byteCount: Long) {
        var at = offset
        var remaining = byteCount
        while (remaining > 0) {
            val count = minOf(remaining, 8192)
            val part = Buffer()
            source.copyTo(part, at, count)
            write(part.readByteArray())
            at += count
            remaining -= count
        }
    }

    @Synchronized fun complete() = end("COMPLETE", null)
    @Synchronized fun partial(error: Exception) = end("PARTIAL", error.javaClass.simpleName)
    @Synchronized fun stopped() = end("PARTIAL", null)

    private fun end(state: String, error: String?) {
        if (finished) return
        finished = true
        // Close remains necessary after a recording I/O failure, even when the session stops accepting writes.
        try { output?.close() } catch (failure: IOException) { session.io { throw failure } }
        output = null
        descriptor.put("state", if (session.index.has("recordingError")) "RECORDING_FAILED" else state)
        error?.let { descriptor.put("error", it) }
        changed()
    }
}
