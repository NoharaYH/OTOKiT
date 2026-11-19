package com.otokit.noharayh.score

import android.database.sqlite.SQLiteException
import android.util.Log
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import org.json.JSONException
import java.io.InputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Score-owned derivative work, independent of the transfer service's lifetime. */
internal class ScoreData(
    private val html: FileHtmlCaptures,
    private val snapshots: ScoreSnapshots,
    private val database: ScoreDatabase,
    private val taskActive: (String) -> Boolean = { false },
) : HtmlCaptures by html, ScoreArchiveQueries, ScoreArchiveActions, PersonalScoreQueries by database {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "ScoreImport") }
    private val failed = ConcurrentHashMap.newKeySet<HtmlBatchRef>()
    private var recovered = false

    fun startRecovery() {
        worker.execute {
            recoverInterrupted()
            ScoreGame.entries.forEach { game ->
                html.list(game).asReversed().forEach(::process)
            }
        }
    }

    /** Both the startup worker and capture coordinator call this before using the archive. */
    @Synchronized
    override fun recoverInterrupted() {
        if (recovered) return
        html.recoverInterrupted()
        recovered = true
    }

    override fun recordEvidence(page: HtmlPageRef, evidence: HtmlPageEvidence) {
        html.recordEvidence(page, evidence)
        if (!page.kind.isDiagnostic && evidence.validity in ScoreSnapshots.readable) submit(page.batch)
    }

    override fun finish(batch: HtmlBatchRef, status: HtmlBatchStatus) {
        html.finish(batch, status)
        submit(batch)
    }

    private fun submit(batch: HtmlBatchRef) = worker.execute { process(batch) }

    private fun process(batch: HtmlBatchRef) {
        try {
            val record = html.read(batch)
            val file = snapshots.advance(record, html::open)
            if (file != null) {
                database.importFile(record.batch, file)
                failed.remove(batch)
            } else if (record.status != HtmlBatchStatus.RUNNING && !record.htmlRemoved && !snapshots.wasRemoved(batch)) {
                failed.add(batch)
            }
        } catch (error: IOException) {
            fail(batch, error)
        } catch (error: JSONException) {
            fail(batch, error)
        } catch (error: SQLiteException) {
            fail(batch, error)
        } catch (error: IllegalArgumentException) {
            fail(batch, error)
        } catch (error: IllegalStateException) {
            fail(batch, error)
        }
    }

    private fun fail(batch: HtmlBatchRef, error: Exception) {
        failed.add(batch)
        Log.w("ScoreImport", batch.game.key + "/" + batch.id + ": " + error.javaClass.simpleName)
    }

    override fun listBatches(): List<ScoreArchiveBatch> =
        ScoreGame.entries.flatMap { html.list(it) }.sortedByDescending { it.startedAt }.map { detail(it).batch }

    override fun detail(batch: HtmlBatchRef): ScoreArchiveDetail {
        val record = html.read(batch)
        val pages = record.pages.filter { !it.kind.isDiagnostic }
        return ScoreArchiveDetail(
            ScoreArchiveBatch(record.batch, record.status, pages.size, pages.sumOf { it.bytes }, snapshots.reference(record)),
            pages,
        )
    }

    override fun previewHtml(page: HtmlPageRef): ScoreTextPreview {
        require(!page.kind.isDiagnostic)
        return preview(html.open(page))
    }

    override fun previewJson(snapshot: ScoreSnapshotRef): ScoreTextPreview {
        val batch = html.read(snapshot.batch)
        val file = snapshots.finalFile(batch)
        require(snapshot == snapshots.reference(batch))
        return preview(file.inputStream())
    }

    override fun remove(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>, done: (ScoreArchiveRemoval) -> Unit) {
        worker.execute {
            val removed = mutableSetOf<HtmlBatchRef>()
            var cleanupPending = 0
            for (batch in batches) {
                archiveOperation {
                    val record = html.read(batch)
                    if (record.status == HtmlBatchStatus.RUNNING || taskActive(record.taskId)) return@archiveOperation
                    val cleaned = when (format) {
                        ScoreArchiveFormat.Html -> html.removeArchive(batch)
                        ScoreArchiveFormat.Json -> snapshots.removeArchive(record)
                    }
                    removed += batch
                    failed.remove(batch)
                    if (!cleaned) cleanupPending++
                }
            }
            done(ScoreArchiveRemoval(removed, cleanupPending))
        }
    }

    override fun export(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>,
                        destination: ((java.io.OutputStream) -> Unit) -> Unit, done: (Boolean) -> Unit) {
        worker.execute {
            done(archiveOperation {
                destination { output ->
                    ZipOutputStream(output.buffered()).use { zip ->
                        for (batch in batches) {
                            val record = html.read(batch)
                            val directory = "${batch.game.key}/${batch.id}"
                            when (format) {
                                ScoreArchiveFormat.Html -> {
                                    val pages = record.pages.filterNot { it.kind.isDiagnostic }
                                    check(pages.isNotEmpty())
                                    pages.forEach { page -> zip.add("$directory/${page.fileName}", html.open(page)) }
                                }
                                ScoreArchiveFormat.Json -> {
                                    val snapshot = checkNotNull(snapshots.reference(record))
                                    zip.add("$directory/${snapshot.name}", snapshots.finalFile(record).inputStream())
                                }
                            }
                        }
                    }
                }
            })
        }
    }

    private fun ZipOutputStream.add(name: String, source: InputStream) = source.use {
        putNextEntry(ZipEntry(name))
        it.copyTo(this)
        closeEntry()
    }

    private inline fun archiveOperation(action: () -> Unit): Boolean = try {
        action()
        true
    } catch (error: Exception) {
        Log.w("ScoreArchive", error.javaClass.simpleName)
        false
    }

    override fun importProgress(batch: HtmlBatchRef): ScoreImportProgress =
        database.importProgress(batch) ?: ScoreImportProgress(if (batch in failed) ScoreImportState.FAILED else ScoreImportState.WAITING)

    private fun preview(source: InputStream): ScoreTextPreview = source.bufferedReader().use { reader ->
        val buffer = CharArray(64 * 1024 + 1)
        var count = 0
        while (count < buffer.size) {
            val n = reader.read(buffer, count, buffer.size - count)
            if (n < 0) break
            count += n
        }
        val truncated = count == buffer.size
        val size = if (truncated && buffer[count - 2].isHighSurrogate()) count - 2 else count - if (truncated) 1 else 0
        ScoreTextPreview(String(buffer, 0, size), truncated)
    }
}
