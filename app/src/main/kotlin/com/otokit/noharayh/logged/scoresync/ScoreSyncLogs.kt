package com.otokit.noharayh.logged.scoresync

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/** 应用作用域的传分日志入口。文件操作由调用方在工作线程执行。 */
internal class ScoreSyncLogs(
    root: File,
    private val repositoryUrl: String,
    private val version: String,
    private val htmlAttachments: (game: String, batchId: String) -> List<ScoreSyncLogAttachment> = { _, _ -> emptyList() },
    private val redactHtml: (InputStream, OutputStream) -> Unit = { _, _ -> throw IOException("HTML redaction unavailable") },
) {
    internal val files = ScoreSyncLogFiles(root)
    private val sessions = ConcurrentHashMap<String, ScoreSyncLogSession>()
    @Volatile var recordingError: String? = null
        private set

    init { recover() }

    @Synchronized fun start(taskId: String, game: String, startedAt: Instant,
                            configuration: Map<String, String> = emptyMap()): ScoreSyncLogSession? = boundary {
        val directory = files.directory(game, taskId)
        check(!directory.exists()) { "Log task already exists" }
        check(directory.mkdirs()) { "Cannot create log directory" }
        val zone = ZoneId.systemDefault()
        val stamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(zone).format(startedAt)
        val index = JSONObject().put("version", 1).put("taskId", taskId).put("game", game)
            .put("startedAt", startedAt.toString()).put("fileStamp", stamp).put("zone", zone.id)
            .put("status", "RUNNING").put("writing", true).put("sequence", 0).put("requestSequence", 0)
            .put("repository", repositoryUrl).put("appVersion", version).put("configuration", JSONObject(configuration))
        val session = ScoreSyncLogSession(files, directory, index)
        files.writeIndex(directory, index)
        sessions[taskId] = session
        session.event("HEADER", "仓库：$repositoryUrl", mapOf("appVersion" to version))
        session.event("PREPARING", "开始记录传分任务", configuration)
        session
    }

    /** 只找既有日志；启动恢复的成绩工作可重新 retain 同一记录，不补造过去事件。 */
    @Synchronized fun find(taskId: String): ScoreSyncLogSession? = findExisting(taskId, true)

    private fun findExisting(taskId: String, recordFailure: Boolean): ScoreSyncLogSession? {
        sessions[taskId]?.let { return it }
        return boundary(recordFailure) {
            val directory = files.directories().firstOrNull { it.name == taskId } ?: return@boundary null
            ScoreSyncLogSession(files, directory, files.index(directory)).also { sessions[taskId] = it }
        }
    }

    fun list(): List<ScoreSyncLogSummary> = files.directories().mapNotNull { directory ->
        boundary {
            val index = files.index(directory)
            files.summary(directory, sessions[index.getString("taskId")]?.snapshotIndex() ?: index)
        }
    }.sortedByDescending { it.ref.startedAt }

    fun preview(ref: ScoreSyncLogRef): ScoreSyncLogPreview {
        val directory = files.directory(ref.game, ref.taskId)
        synchronized(lock(ref)) {
            val index = files.index(directory)
            val file = File(directory, ScoreSyncLogSession.prefix(index) + ".jsonl")
            val result = StringBuilder()
            var truncated = false
            file.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    val redacted = ScoreSyncLogRedactor.json(JSONObject(line)).toString()
                    if (result.length + redacted.length > 64 * 1024) { truncated = true; break }
                    result.append(redacted).append('\n')
                }
            }
            return ScoreSyncLogPreview(result.toString(), truncated)
        }
    }

    fun remove(refs: Set<ScoreSyncLogRef>): ScoreSyncLogRemoval {
        val removed = linkedSetOf<ScoreSyncLogRef>()
        val skipped = linkedSetOf<ScoreSyncLogRef>()
        refs.forEach { ref ->
            synchronized(lock(ref)) {
                val success = boundary {
                    val directory = files.directory(ref.game, ref.taskId)
                    val index = sessions[ref.taskId]?.snapshotIndex() ?: files.index(directory)
                    if (index.optBoolean("writing")) false else directory.deleteRecursively()
                } == true
                if (success) { sessions.remove(ref.taskId); removed += ref } else skipped += ref
            }
        }
        return ScoreSyncLogRemoval(removed, skipped)
    }

    /** 与成绩导出相同的流式目的地契约；不启动网络请求，不修改原始文件。 */
    fun export(refs: Set<ScoreSyncLogRef>, redact: Boolean, includeHtml: Boolean,
               destination: ((OutputStream) -> Unit) -> Unit, done: (Boolean) -> Unit) {
        // 短锁只解析稳定的记录所有者，ZIP 写出不占用应用级入口锁。
        val selected = boundary(false) {
            synchronized(this) {
                refs.sortedBy { it.taskId }.map { ref ->
                    ref to (findExisting(ref.taskId, false) ?: throw IOException("Selected log is unavailable"))
                }
            }
        }
        if (selected == null) { done(false); return }
        fun locked(at: Int, body: () -> Unit) {
            if (at == selected.size) body() else synchronized(selected[at].second) { locked(at + 1, body) }
        }
        val success = boundary(false) {
            locked(0) {
                val indexes = selected.associate { (ref, session) ->
                    if (sessions[ref.taskId] !== session) throw IOException("Selected log was removed")
                    val index = session.snapshotIndex()
                    if (files.ref(index) != ref) throw IOException("Selected log reference changed")
                    ref to index
                }
                destination { output -> ScoreSyncLogExport.write(files, refs, redact, includeHtml,
                    htmlAttachments, redactHtml, indexes, output) }
            }
            true
        } == true
        done(success)
    }

    private fun lock(ref: ScoreSyncLogRef): Any = find(ref.taskId) ?: this

    private fun recover() {
        files.directories().forEach { directory -> boundary {
            val index = files.index(directory)
            if (index.optBoolean("writing")) {
                files.recoverTail(File(directory, ScoreSyncLogSession.prefix(index) + ".jsonl"))
                index.put("writing", false).put("status", "INTERRUPTED").put("interrupted", true)
                directory.listFiles().orEmpty().filter { it.name.endsWith("_Request.json") }.forEach { metadata ->
                    try {
                        val request = JSONObject(metadata.readText())
                        listOf("requestBody", "responseBody").forEach { field ->
                            request.optJSONObject(field)?.let { if (it.optString("state") == "WRITING") it.put("state", "PARTIAL") }
                        }
                        metadata.writeText(request.toString(), Charsets.UTF_8)
                    } catch (error: JSONException) {
                        index.put("recordingError", "InterruptedRequestMetadata")
                    } catch (error: IOException) {
                        index.put("recordingError", error.javaClass.simpleName)
                    }
                }
                files.writeIndex(directory, index)
            }
        } }
    }

    private inline fun <T> boundary(recordFailure: Boolean = true, action: () -> T): T? = try { action() }
    catch (error: IOException) { if (recordFailure) recordingError = error.javaClass.simpleName; null }
    catch (error: SecurityException) { if (recordFailure) recordingError = error.javaClass.simpleName; null }
    catch (error: JSONException) { if (recordFailure) recordingError = error.javaClass.simpleName; null }
    catch (error: IllegalStateException) { if (recordFailure) recordingError = error.javaClass.simpleName; null }
    catch (error: IllegalArgumentException) { if (recordFailure) recordingError = error.javaClass.simpleName; null }
}
