package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.ScoreGame
import java.io.Closeable
import java.time.Instant

data class UploadReport(val taskId: String, val startedAt: Instant, val stage: CaptureStage, val pages: List<UploadPageResult>)

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

    internal fun publish(game: ScoreGame, report: UploadReport) {
        latest[game] = report
        observers.toList().forEach { it() }
    }
}
