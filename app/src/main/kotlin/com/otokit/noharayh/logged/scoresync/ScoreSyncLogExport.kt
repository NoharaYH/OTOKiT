package com.otokit.noharayh.logged.scoresync

import org.json.JSONObject
import java.io.File
import java.io.FilterOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal object ScoreSyncLogExport {
    fun write(files: ScoreSyncLogFiles, refs: Set<ScoreSyncLogRef>, redact: Boolean, includeHtml: Boolean,
              attachments: (String, String) -> List<ScoreSyncLogAttachment>,
              redactHtml: (InputStream, OutputStream) -> Unit, indexes: Map<ScoreSyncLogRef, JSONObject>, destination: OutputStream) {
        // 全部附件先校验可用再开始创建 ZIP，避免默默少打包关联 HTML。
        val batches = refs.associateWith { ref ->
            val directory = files.directory(ref.game, ref.taskId)
            val index = indexes.getValue(ref)
            if (index.optBoolean("writing")) throw IOException("Log still has active writers")
            val batch = index.optString("batchId")
            val html = if (includeHtml) {
                if (batch.isEmpty()) throw IOException("Log has no associated HTML batch")
                attachments(ref.game, batch).also { if (it.isEmpty()) throw IOException("Associated HTML unavailable") }
            } else emptyList()
            Triple(directory, index, html)
        }
        ZipOutputStream(destination.buffered()).use { zip ->
            batches.forEach { (ref, batch) ->
                val (directory, index, html) = batch
                val path = "${ref.game}/${ref.taskId}/"
                val prefix = ScoreSyncLogSession.prefix(index)
                val sourceFiles = directory.listFiles().orEmpty().filter { it.isFile && !it.name.endsWith(".part") }
                val descriptors = mutableMapOf<String, JSONObject>()
                if (redact) {
                    sourceFiles.filter { it.name.endsWith("_Request.json") }.forEach { metadata ->
                        val request = JSONObject(metadata.readText(Charsets.UTF_8))
                        listOf("requestBody", "responseBody").forEach { key ->
                            request.optJSONObject(key)?.takeIf { it.has("file") }?.let { descriptors[it.getString("file")] = it }
                        }
                    }
                }
                val exportedBytes = mutableMapOf<String, Long>()
                sourceFiles.filter { it.name.endsWith(".body") }.forEach { file ->
                    entry(zip, path + "requests/" + file.name) { output ->
                        val counted = Counter(output)
                        file.inputStream().use { source ->
                            if (redact) ScoreSyncLogRedactor.body(source,
                                descriptors[file.name] ?: throw IOException("Missing body descriptor"), counted, redactHtml)
                            else source.copyTo(counted)
                        }
                        exportedBytes[file.name] = counted.bytes
                    }
                }
                sourceFiles.filterNot { it.name.endsWith(".body") }.forEach { file ->
                    val name = when {
                        file.name == "index.json" -> prefix + "_Index.json"
                        file.name.endsWith("_Request.json") -> "requests/" + file.name
                        else -> file.name
                    }
                    entry(zip, path + name) { output ->
                        when {
                            !redact -> file.inputStream().use { it.copyTo(output) }
                            file.extension == "jsonl" -> file.forEachLine(Charsets.UTF_8) { line ->
                                output.write((ScoreSyncLogRedactor.json(JSONObject(line)).toString() + "\n").toByteArray(Charsets.UTF_8))
                            }
                            file.extension == "json" -> {
                                val original = if (file.name == "index.json") index else JSONObject(file.readText(Charsets.UTF_8))
                                val data = ScoreSyncLogRedactor.json(original)
                                listOf("requestBody", "responseBody").forEach { key -> data.optJSONObject(key)?.let { body ->
                                    exportedBytes[body.optString("file")]?.let { bytes ->
                                        body.put("capturedBytes", body.optLong("bytes"))
                                            .put("bytes", bytes).put("originalContentEncoding", body.optString("contentEncoding"))
                                            .put("contentEncoding", "").put("exportRedacted", true)
                                    }
                                } }
                                data.put("exportRedacted", true)
                                output.write(data.toString().toByteArray(Charsets.UTF_8))
                            }
                            else -> throw IOException("Unknown log file type")
                        }
                    }
                }
                html.distinctBy { it.name }.forEach { attachment ->
                    if (attachment.name.contains('/') || attachment.name.contains('\\') || !attachment.name.endsWith(".html")) {
                        throw IOException("Invalid HTML attachment name")
                    }
                    entry(zip, path + "html/" + attachment.name) { output ->
                        attachment.open().use { source -> if (redact) redactHtml(source, output) else source.copyTo(output) }
                    }
                }
            }
        }
    }

    private inline fun entry(zip: ZipOutputStream, name: String, write: (OutputStream) -> Unit) {
        zip.putNextEntry(ZipEntry(name))
        write(zip)
        zip.closeEntry()
    }

    private class Counter(output: OutputStream) : FilterOutputStream(output) {
        var bytes = 0L
        override fun write(value: Int) { out.write(value); bytes++ }
        override fun write(buffer: ByteArray, offset: Int, length: Int) { out.write(buffer, offset, length); bytes += length }
    }
}
