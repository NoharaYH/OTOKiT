package com.otokit.noharayh.transfer

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.task.BatchUploadTask
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BatchUploadTaskTest {
    @get:Rule val temp = TemporaryFolder()

    @Test fun sameBatchGoesToChosenAccountsAndUnknownSubmissionIsNotReplayed() {
        val store = FileHtmlCaptures(temp.newFolder())
        val kinds = listOf(HtmlPageKind.PLAYER, HtmlPageKind.RECENT, HtmlPageKind.EXPERT)
        val batch = store.begin(ScoreGame.MAIMAI, kinds, "new-scores")
        for (kind in kinds) {
            val page = store.save(batch, kind, kind.name.byteInputStream()) { false }
            store.recordEvidence(page, HtmlPageEvidence(HtmlValidity.VALID))
        }
        store.finish(batch, HtmlBatchStatus.COMPLETED)
        val targets = listOf(UploadTarget(OAuthProvider.DIVING_FISH, "fish"), UploadTarget(OAuthProvider.LXNS, "snow"))
        val request = CaptureRequest(batch.game, listOf(HtmlPageKind.EXPERT), "new-scores", uploadTargets = targets)
        val record = store.read(batch)
        assertTrue(record.pages.last().fileName.matches(Regex("MaiRecord_HTML_[0-9]{14}_Expert\\.html")))
        val sent = mutableListOf<Pair<String, HtmlPageKind>>()
        val task = BatchUploadTask(temp.newFolder()) { target, page ->
            assertEquals(page.kind.name, store.open(page).bufferedReader().use { it.readText() })
            sent += target.subject to page.kind
            UploadReceipt(if (target.provider == OAuthProvider.LXNS) UploadOutcome.UNKNOWN else UploadOutcome.ACCEPTED)
        }
        val receipts = mutableListOf<UploadPageResult>()
        assertEquals(CaptureStage.PARTIAL, task.run(request, record, {}, receipts::add))
        assertEquals(listOf("fish" to HtmlPageKind.EXPERT, "snow" to HtmlPageKind.PLAYER), sent)
        assertEquals(UploadOutcome.BLOCKED, receipts.last().receipt.outcome)
        sent.clear()
        assertEquals(CaptureStage.PARTIAL, task.run(request, record, {}, {}))
        assertTrue(sent.isEmpty())
        assertThrows(IllegalArgumentException::class.java) { task.run(request.copy(launchId = "different"), record, {}, {}) }
        assertThrows(IllegalArgumentException::class.java) {
            task.run(request.copy(uploadTargets = listOf(UploadTarget(OAuthProvider.LXNS, "other"))), record, {}, {})
        }
    }
}
