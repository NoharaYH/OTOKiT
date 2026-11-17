package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties

class FileHtmlCapturesTest {
    @get:Rule val temp = TemporaryFolder()
    private fun index() = temp.root.walkTopDown().first { it.name == "batch.properties" }
    private fun status() = Properties().apply { index().inputStream().use { load(it) } }.getProperty("status")

    @Test fun canonicalNamesRoundTripAndIndexRequireVerifiedPages() {
        val store = FileHtmlCaptures(temp.root)
        val batch = store.begin(ScoreGame.CHUNITHM, listOf(HtmlPageKind.MASTER, HtmlPageKind.ULTIMA), "task")
        val master = store.save(batch, HtmlPageKind.MASTER, "<html>score</html>".byteInputStream()) { false }
        val ultima = store.save(batch, HtmlPageKind.ULTIMA, "<html>empty</html>".byteInputStream()) { false }
        assertTrue(master.fileName.matches(Regex("ChuniRecord_HTML_[0-9]{14}_Master.html")))
        assertEquals(master.fileName.substringBeforeLast('_'), ultima.fileName.substringBeforeLast('_'))
        assertEquals("<html>score</html>", store.open(master).bufferedReader().use { it.readText() })
        assertThrows(IllegalStateException::class.java) { store.finish(batch, HtmlBatchStatus.COMPLETED) }
        store.recordEvidence(master, HtmlPageEvidence(HtmlValidity.VALID, 1, 1))
        store.recordEvidence(ultima, HtmlPageEvidence(HtmlValidity.VALID_EMPTY_SCORES, 1, 0))
        store.finish(batch, HtmlBatchStatus.COMPLETED)
        assertEquals("COMPLETED", status())
        val reopened = FileHtmlCaptures(temp.root)
        assertEquals(listOf(batch), reopened.list(ScoreGame.CHUNITHM))
        val record = reopened.read(batch)
        assertEquals(HtmlBatchStatus.COMPLETED, record.status)
        assertEquals(listOf(master, ultima), record.pages)
        assertEquals(0, record.evidence.getValue(HtmlPageKind.ULTIMA).scoreCount)
        assertTrue(index().readText().contains("UNIDENTIFIED"))
    }

    @Test fun interruptedBatchRetainsPublishedHtmlAndRemovesOnlyOrphans() {
        val store = FileHtmlCaptures(temp.root)
        val batch = store.begin(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "task")
        val page = store.save(batch, HtmlPageKind.MASTER, "original".byteInputStream()) { false }
        val directory = index().parentFile
        File(directory, "orphan.html").writeText("partial-index")
        File(directory, "pending.html.part").writeText("partial")
        FileHtmlCaptures(temp.root).recoverInterrupted()
        assertEquals("INTERRUPTED", status())
        assertFalse(File(directory, "orphan.html").exists())
        assertFalse(File(directory, "pending.html.part").exists())
        assertEquals("original", store.open(page).bufferedReader().use { it.readText() })
    }

    @Test fun tamperedArchiveAndTraversalReferencesAreRejected() {
        val store = FileHtmlCaptures(temp.root)
        val batch = store.begin(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "task")
        val page = store.save(batch, HtmlPageKind.MASTER, "abc".byteInputStream()) { false }
        assertThrows(IllegalArgumentException::class.java) { store.open(page.copy(fileName = "../outside.html")) }
        File(index().parentFile, page.fileName).writeText("xyz")
        assertThrows(IllegalStateException::class.java) { store.open(page) }
    }
}
