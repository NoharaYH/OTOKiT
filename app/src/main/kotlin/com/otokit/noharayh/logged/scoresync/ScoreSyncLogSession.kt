package com.otokit.noharayh.logged.scoresync

import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/** 只汇总日志写入者；传分与成绩处理的真实状态仍由各自业务拥有。 */
internal class ScoreSyncLogSession internal constructor(
    private val files: ScoreSyncLogFiles,
    internal val directory: File,
    internal val index: JSONObject,
) {
    private val clock = DateTimeFormatter.ofPattern("HHmmss").withZone(ZoneId.of(index.getString("zone")))
    private var pending = 0
    private var finished = !index.optBoolean("writing")
    val httpInterceptor get() = ScoreSyncHttpCapture(this, "HTTP")

    @Synchronized fun event(stage: String, message: String, data: Map<String, String> = emptyMap()) = io {
        val sequence = index.optInt("sequence") + 1
        index.put("sequence", sequence)
        val time = time()
        val event = JSONObject().put("sequence", sequence).put("time", time).put("stage", stage)
            .put("message", "[$time] $message").put("data", JSONObject(data))
        File(directory, prefix(index) + ".jsonl").appendText(event.toString() + "\n", Charsets.UTF_8)
        files.writeIndex(directory, index)
    }

    @Synchronized fun bindBatch(batchId: String, fileStamp: String) = io {
        require(fileStamp.matches(Regex("[0-9]{14}")))
        val old = prefix(index)
        index.put("batchId", batchId).put("fileStamp", fileStamp)
        val next = prefix(index)
        if (old != next) directory.listFiles().orEmpty().filter { it.name.startsWith(old) }.forEach { file ->
            val renamed = File(directory, next + file.name.removePrefix(old))
            if (!file.renameTo(renamed)) throw IOException("Cannot rename log batch")
            if (renamed.name.endsWith("_Request.json")) renamed.writeText(renamed.readText().replace(old, next), Charsets.UTF_8)
        }
        files.writeIndex(directory, index)
        event("ARCHIVE", "关联 HTML 采集批次", mapOf("batchId" to batchId, "fileStamp" to fileStamp))
    }

    @Synchronized fun retain(): Closeable {
        val resuming = finished && pending == 0
        pending++
        index.put("writing", true)
        io { files.writeIndex(directory, index) }
        if (resuming) event("LOCAL_PROCESSING", "恢复本地处理", mapOf("originalStatus" to index.getString("status")))
        val released = AtomicBoolean(false)
        return Closeable {
            if (released.compareAndSet(false, true)) synchronized(this) {
                pending--
                sealIfReady()
            }
        }
    }

    @Synchronized fun finish(status: String) {
        finished = true
        index.put("status", status)
        io { files.writeIndex(directory, index) }
        event("TRANSFER", "传分流程结束", mapOf("outcome" to status))
        sealIfReady()
    }

    private fun sealIfReady() {
        if (finished && pending == 0) {
            index.put("writing", false)
            // 即便正文记录已失败，也单独尝试保存真实的写入者终态。
            persistFinalIndex()
        }
    }

    @Synchronized internal fun snapshotIndex(): JSONObject = JSONObject(index.toString())
        .put("writing", !finished || pending > 0)

    private fun persistFinalIndex() {
        try { files.writeIndex(directory, index) }
        catch (error: IOException) { index.put("recordingError", error.javaClass.simpleName) }
        catch (error: SecurityException) { index.put("recordingError", error.javaClass.simpleName) }
    }

    @Synchronized internal fun beginHttp(stage: String, method: String, url: String,
                                        headers: List<Pair<String, String>>): ScoreSyncHttpRecord {
        val number = index.optInt("requestSequence") + 1
        index.put("requestSequence", number)
        return ScoreSyncHttpRecord(this, stage, number, method, url, headers)
    }

    fun localHttp(stage: String, method: String, url: String, requestHeaders: List<Pair<String, String>>,
                  status: Int, responseHeaders: List<Pair<String, String>>, responseBody: ByteArray,
                  requestBody: ByteArray = byteArrayOf()) {
        val record = beginHttp(stage, method, url, requestHeaders)
        record.body(true, "application/octet-stream", "").apply { write(requestBody); complete() }
        record.response(status, "HTTP/1.1", responseHeaders)
        val contentType = responseHeaders.firstOrNull { it.first.equals("Content-Type", true) }?.second.orEmpty()
        record.body(false, contentType, "").apply { write(responseBody); complete() }
        record.close()
    }

    internal fun time(): String = clock.format(Instant.now())

    @Synchronized internal fun canRecord(): Boolean = !index.has("recordingError")

    /** 单个日志 I/O 边界：失败标记不进入网络异常／重试路径。 */
    @Synchronized internal fun io(action: () -> Unit) {
        if (index.has("recordingError")) return
        try { action() }
        catch (error: IOException) { failed(error) }
        catch (error: SecurityException) { failed(error) }
    }

    private fun failed(error: Exception) {
        index.put("recordingError", error.javaClass.simpleName)
        try { files.writeIndex(directory, index) } catch (_: IOException) { /* 原目录也可能已不可写。 */ }
        catch (_: SecurityException) { /* 保留内存中的记录失败事实。 */ }
    }

    internal companion object {
        fun prefix(index: JSONObject): String =
            (if (index.getString("game") == "maimai") "MaiRecord" else "ChuRecord") + "_Log_" + index.getString("fileStamp")
    }
}
