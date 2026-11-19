package com.otokit.noharayh.score

import android.database.sqlite.SQLiteException
import android.util.Log
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import org.json.JSONException
import java.io.InputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** Score-owned derivative work, independent of the transfer service's lifetime. */
internal class ScoreData(
    private val html: HtmlCaptures,
    private val snapshots: ScoreSnapshots,
    private val database: ScoreDatabase,
) : HtmlCaptures by html, ScoreArchiveQueries, PersonalScoreQueries by database {
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
            } else if (record.status != HtmlBatchStatus.RUNNING) {
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

    override fun list(game: ScoreGame, offset: Int, limit: Int): ScorePage<ScoreArchiveBatch> {
        require(offset >= 0 && limit in 1..200)
        val batches = html.list(game).drop(offset).take(limit + 1)
        return ScorePage(batches.take(limit).map { detail(it).batch }, (offset + limit).takeIf { batches.size > limit })
    }

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
        require(snapshot.id == ScoreSnapshots.id(batch.batch) && snapshot.name == file.name)
        return preview(file.inputStream())
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
