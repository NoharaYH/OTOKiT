package com.otokit.noharayh.score

import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.score.json.ScoreDatabase
import com.otokit.noharayh.score.json.ScoreSnapshots
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ScoreProcessingRecordingTest {
    @Test fun submissionRetainsSynchronouslyAndReleasesAfterProcessingFailure() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val root = Files.createTempDirectory(context.cacheDir.toPath(), "score-recording-").toFile()
        val dbName = "score-recording-${UUID.randomUUID()}.db"
        val database = ScoreDatabase(context, dbName)
        val entered = CountDownLatch(1)
        val proceed = CountDownLatch(1)
        val released = CountDownLatch(1)
        val retained = AtomicInteger()
        val failures = AtomicInteger()
        try {
            val html = FileHtmlCaptures(File(root, "html"))
            val blockedSnapshots = File(root, "json").apply { writeText("not a directory") }
            val snapshots = ScoreSnapshots(blockedSnapshots) { error("No catalog should be read") }
            val archives = ScoreData(html, snapshots, database, processing = ScoreProcessingObserver(
                queued = { record ->
                    assertEquals("retention-fixture", record.taskId)
                    retained.incrementAndGet()
                    Closeable { retained.decrementAndGet(); released.countDown() }
                }, event = { _, message, _ ->
                    if (message == "开始检查并推进 HTML 成绩派生") {
                        entered.countDown()
                        assertTrue(proceed.await(5, TimeUnit.SECONDS))
                    }
                    if (message == "本地成绩处理失败") failures.incrementAndGet()
                }))
            val batch = html.begin(ScoreGame.MAIMAI, listOf(HtmlPageKind.PLAYER), "retention-fixture", Instant.now())
            "<div class='name_block'>fixture</div>".byteInputStream().use {
                val page = html.save(batch, HtmlPageKind.PLAYER, it) { false }
                html.recordEvidence(page, HtmlPageEvidence(HtmlValidity.VALID))
            }
            archives.finish(batch, HtmlBatchStatus.COMPLETED)
            assertEquals(1, retained.get())
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            assertEquals(1, retained.get())
            proceed.countDown()
            assertTrue(released.await(5, TimeUnit.SECONDS))
            assertEquals(0, retained.get())
            assertEquals(1, failures.get())
        } finally {
            proceed.countDown()
            database.close()
            context.deleteDatabase(dbName)
            root.deleteRecursively()
        }
    }
}
