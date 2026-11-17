package com.otokit.noharayh.transfer.net

import java.io.Closeable
import java.io.InputStream
import java.net.URI

internal interface NetSession : Closeable {
    data class Page(val uri: URI, val contentType: String, val stream: InputStream, val status: Int)
    fun authorizationUrl(game: String): URI
    fun login(callback: URI, saveDiagnostic: (Page) -> Unit)
    fun <T> read(uri: URI, post: String? = null, consume: (Page) -> T): T
}

internal class NetHttpException(val status: Int) : java.io.IOException("NET returned HTTP $status")
internal class NetProtocolException(val issue: com.otokit.noharayh.transfer.CaptureProtocolIssue) : IllegalStateException(issue.name)
