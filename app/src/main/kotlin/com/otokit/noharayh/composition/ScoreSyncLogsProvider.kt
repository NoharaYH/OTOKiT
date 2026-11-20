package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.BuildConfig
import com.otokit.noharayh.logged.scoresync.ScoreSyncHttpCapture
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogAttachment
import com.otokit.noharayh.logged.scoresync.ScoreSyncLogs
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.score.html.HtmlExportRedactor
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.TransferRecording
import java.io.File
import java.time.Instant

private var syncLogs: ScoreSyncLogs? = null

@Synchronized
internal fun scoreSyncLogs(context: Context): ScoreSyncLogs {
    val app = context.applicationContext
    return syncLogs ?: ScoreSyncLogs(File(app.filesDir, "logged/scoresync"),
        "https://github.com/NoharaYH/OTOKiT", BuildConfig.VERSION_NAME,
        htmlAttachments = { game, batchId ->
            val captures = scoreCaptures(app)
            val scoreGame = ScoreGame.entries.first { it.key == game }
            val batch = captures.list(scoreGame).firstOrNull { it.id == batchId }
                ?: error("Associated HTML batch is missing")
            captures.read(batch).pages.filterNot { it.kind.isDiagnostic }.map { page ->
                ScoreSyncLogAttachment(page.fileName) { captures.open(page) }
            }
        }, redactHtml = HtmlExportRedactor::write).also { syncLogs = it }
}

internal fun scoreSyncRecording(context: Context, request: CaptureRequest, startedAt: Instant): TransferRecording? {
    if (!request.saveSyncLog) return null
    val session = scoreSyncLogs(context).start(request.launchId, request.game.key, startedAt, mapOf(
        "difficulties" to request.difficulties.joinToString(",") { it.name },
        "uploadTargets" to request.uploadTargets.joinToString(",") { it.provider.name },
        "keepCallbackCaptureUntilLogin" to request.keepCallbackCaptureUntilLogin.toString(),
        "androidVersion" to android.os.Build.VERSION.RELEASE,
        "deviceModel" to android.os.Build.MODEL,
    )) ?: return null
    return TransferRecording(session::event, { stage -> ScoreSyncHttpCapture(session, stage) },
        { batch -> session.bindBatch(batch.id, scoreCaptures(context).read(batch).fileStamp) }, session::finish,
        { http -> session.localHttp("CALLBACK", http.method, http.url, http.requestHeaders,
            http.status, http.responseHeaders, http.responseBody) }, retain = session::retain)
}
