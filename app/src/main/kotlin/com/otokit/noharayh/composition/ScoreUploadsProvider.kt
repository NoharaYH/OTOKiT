package com.otokit.noharayh.composition

import android.content.Context
import com.otokit.noharayh.score.HtmlCaptures
import com.otokit.noharayh.transfer.task.BatchUploadTask
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import java.io.Closeable
import java.io.File

internal fun scoreUploadFactory(context: Context, archives: HtmlCaptures): ((Closeable) -> Unit) -> BatchUploadTask {
    val app = context.applicationContext
    val directory = File(app.filesDir, "transfer/uploads")
    return { registerCall ->
        val upload = PlatformHtmlUpload(archives, oauthConnections(app)::scoreAccessToken)
        BatchUploadTask(directory) { target, page ->
            upload.send(target.provider, target.subject, page) { registerCall(Closeable(it::cancel)) }
        }
    }
}
