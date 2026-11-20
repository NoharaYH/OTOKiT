package com.otokit.noharayh.platform

import android.content.Context
import android.net.Uri
import com.otokit.noharayh.score.HtmlBatchRef
import com.otokit.noharayh.score.ScoreArchiveActions
import com.otokit.noharayh.score.ScoreArchiveFormat
import java.io.Closeable
import java.util.Locale

/** 成绩导出参数适配；目录授权与分享复用 Android ZIP 交付能力。 */
internal class ScoreArchiveExport(context: Context, private val actions: ScoreArchiveActions) : Closeable {
    private val documents = DocumentZipExport(context)

    fun export(format: ScoreArchiveFormat, batches: Set<HtmlBatchRef>, redact: Boolean,
               written: (Uri) -> Unit, stopped: (Int?) -> Unit) = documents.export(format.directoryName, format.name,
        write = { destination, done -> actions.export(format, batches, redact, destination, done) }, written, stopped)

    override fun close() = documents.close()

    companion object {
        fun open(context: Context, file: Uri, share: Boolean) = DocumentZipExport.open(context, file, share)
    }
}

internal val ScoreArchiveFormat.directoryName get() = name.lowercase(Locale.ROOT)
