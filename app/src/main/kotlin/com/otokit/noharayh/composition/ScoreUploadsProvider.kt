package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.score.HtmlCaptures
import com.otokit.noharayh.auth.ScoreTokenObserver
import com.otokit.noharayh.transfer.TransferRecording
import com.otokit.noharayh.transfer.task.BatchUploadTask
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import java.io.Closeable
import java.io.File

internal fun scoreUploadFactory(context: Context, archives: HtmlCaptures): ((Closeable) -> Unit, TransferRecording?) -> BatchUploadTask {
    val app = context.applicationContext
    val directory = File(app.filesDir, "transfer/uploads")
    return { registerCall, recording ->
        val tokenObserver = recording?.let { record -> ScoreTokenObserver(record.interceptor("AUTH")) {
            record.event("AUTH", it)
        } }
        val upload = PlatformHtmlUpload(archives, { provider, subject, scope ->
            oauthConnections(app).scoreAccessToken(provider, subject, scope, tokenObserver, recording?.retain)
        }, recording = recording)
        BatchUploadTask(directory) { target, page ->
            upload.send(target.provider, target.subject, page) { registerCall(Closeable(it::cancel)) }
        }
    }
}
