package com.otokit.noharayh.transfer.task

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.*
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Properties

/** Owned by the running transfer. No archive discovery and no implicit retry of an ambiguous POST. */
internal class BatchUploadTask(
    private val directory: File,
    private val send: (UploadTarget, HtmlPageRef) -> UploadReceipt,
) {
    fun run(request: CaptureRequest, batch: HtmlBatchRecord, checkActive: () -> Unit,
            report: (UploadPageResult) -> Unit): CaptureStage {
        require(batch.taskId == request.launchId && batch.batch.game == request.game && batch.status == HtmlBatchStatus.COMPLETED)
        val pages = batch.pages.associateBy { it.kind }
        val plan = listOf(HtmlPageKind.PLAYER, HtmlPageKind.RECENT) + request.difficulties
        require(plan == batch.expectedPages)
        val file = File(directory, "${batch.batch.id}.properties")
        val ledger = Properties()
        if (file.exists()) file.inputStream().use(ledger::load)
        ledger.setProperty("taskId", request.launchId)
        ledger.setProperty("startedAt", batch.batch.startedAt.toString())
        var successes = 0
        var incomplete = false
        for (target in request.uploadTargets) {
            val provider = target.provider
            val subjectKey = "${provider.name}.subject"
            val original = ledger.getProperty(subjectKey)
            require(original == null || original == target.subject)
            ledger.setProperty(subjectKey, target.subject)
            var blocked = false
            for (kind in plan) {
                checkActive()
                val page = pages.getValue(kind)
                require(batch.evidence[kind]?.validity in setOf(HtmlValidity.VALID, HtmlValidity.VALID_EMPTY_SCORES))
                val key = "${provider.name}.${kind.name}.${page.sha256}"
                val previous = ledger.getProperty(key)?.let(UploadOutcome::valueOf)
                fun record(receipt: UploadReceipt) {
                    if (ledger.getProperty(key) != receipt.outcome.name) {
                        ledger.setProperty("$key.at", Instant.now().toString())
                    }
                    ledger.setProperty(key, receipt.outcome.name)
                    receipt.httpStatus?.let { ledger.setProperty("$key.http", it.toString()) }
                    receipt.records?.let { ledger.setProperty("$key.records", it.toString()) }
                    persist(file, ledger)
                    report(UploadPageResult(provider, kind, receipt))
                }
                val receipt = when {
                    previous == UploadOutcome.SENDING || previous == UploadOutcome.UNKNOWN -> UploadReceipt(UploadOutcome.UNKNOWN)
                    previous != null && UploadReceipt(previous).accepted -> UploadReceipt(previous,
                        ledger.getProperty("$key.http")?.toIntOrNull(), ledger.getProperty("$key.records")?.toIntOrNull())
                    provider == OAuthProvider.DIVING_FISH && !kind.isDifficulty -> UploadReceipt(UploadOutcome.UNSUPPORTED)
                    blocked -> UploadReceipt(UploadOutcome.BLOCKED)
                    else -> {
                        record(UploadReceipt(UploadOutcome.SENDING))
                        send(target, page)
                    }
                }
                record(receipt)
                if (receipt.accepted) successes++
                else if (receipt.outcome != UploadOutcome.UNSUPPORTED) incomplete = true
                if ((kind == HtmlPageKind.PLAYER && provider == OAuthProvider.LXNS && !receipt.accepted) ||
                    receipt.outcome in setOf(UploadOutcome.AUTH_REQUIRED, UploadOutcome.PERMISSION_DENIED,
                        UploadOutcome.RATE_LIMITED, UploadOutcome.AUTH_UNAVAILABLE)) blocked = true
            }
        }
        return when {
            !incomplete -> CaptureStage.COMPLETED
            successes > 0 -> CaptureStage.PARTIAL
            else -> CaptureStage.FAILED
        }
    }

    private fun persist(file: File, ledger: Properties) {
        directory.mkdirs()
        val tmp = File(directory, file.name + ".tmp")
        FileOutputStream(tmp).use { out -> ledger.store(out, null); out.fd.sync() }
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
}
