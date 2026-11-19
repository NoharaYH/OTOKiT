package com.otokit.noharayh.transfer

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.HtmlValidity
import com.otokit.noharayh.score.ScoreGame
import java.io.Closeable
import java.time.Instant
import java.util.Collections

sealed interface TransferEvent {
    data class Stage(val stage: CaptureStage, val failure: CaptureFailure?, val httpStatus: Int?) : TransferEvent
    data class Saved(val page: HtmlPageKind, val completed: Int, val total: Int) : TransferEvent
    data class Upload(val page: UploadPageResult) : TransferEvent
}

data class TransferLogLine(val at: Instant, val event: TransferEvent)
data class UploadReport(val taskId: String, val startedAt: Instant, val terminal: Boolean,
                        val difficulties: List<HtmlPageKind>, val targets: List<OAuthProvider>, val log: List<TransferLogLine>)

/** Main-thread projection for pages reopened after the service has already stopped. */
object UploadReports {
    private val latest = mutableMapOf<ScoreGame, UploadReport>()
    private val observers = mutableSetOf<() -> Unit>()

    fun latest(game: ScoreGame): UploadReport? = latest[game]

    fun observe(observer: () -> Unit): Closeable {
        observers.add(observer)
        observer()
        return Closeable { observers.remove(observer) }
    }

    internal fun publish(snapshot: CaptureSnapshot, observedAt: Instant) {
        val request = snapshot.request
        val game = request.game
        val last = latest[game]
        if (last != null && last.taskId != request.launchId && snapshot.startedAt < last.startedAt) return
        val previous = last?.takeIf { it.taskId == request.launchId }
        if (previous?.terminal == true) return
        val log = previous?.log.orEmpty().toMutableList()
        fun append(event: TransferEvent, at: Instant = observedAt) {
            if (log.none { it.event == event }) log.add(TransferLogLine(at, event))
        }
        val stage = TransferEvent.Stage(snapshot.stage, snapshot.failure, snapshot.httpStatus)
        if (!snapshot.terminal) append(stage, if (snapshot.stage == CaptureStage.PREPARING) snapshot.startedAt else observedAt)
        var saved = 0
        for (page in snapshot.pages) {
            if (page.responseStatus == 200 && page.evidence.validity in setOf(HtmlValidity.VALID, HtmlValidity.VALID_EMPTY_SCORES)) {
                append(TransferEvent.Saved(page.page.kind, ++saved, snapshot.totalPages), page.page.capturedAt)
            }
        }
        snapshot.uploads.forEach { append(TransferEvent.Upload(it)) }
        if (snapshot.terminal) append(stage)
        if (previous != null && log.size == previous.log.size) return
        latest[game] = UploadReport(request.launchId, snapshot.startedAt, snapshot.terminal,
            previous?.difficulties ?: Collections.unmodifiableList(request.difficulties.toList()),
            previous?.targets ?: Collections.unmodifiableList(request.uploadTargets.map { it.provider }),
            Collections.unmodifiableList(log))
        observers.toList().forEach { it() }
    }
}
