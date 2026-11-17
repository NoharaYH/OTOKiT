package com.otokit.noharayh.transfer.test

import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Debug projection/export only. No login, URL plan or mutable task facts live here. */
class CaptureTestService : NetCaptureService() {
    override fun notificationIntent() = Intent(this, CaptureTestActivity::class.java).putExtra("start", false)

    override fun onSnapshot(snapshot: CaptureSnapshot) {
        val batch = snapshot.batch ?: return
        val root = File(filesDir, "transfer-capture").apply { mkdirs() }
        val directory = File(root, batch.id).apply { mkdirs() }
        fun export(captured: CapturedHtml): JSONObject {
            val ref = captured.page
            val alias = when (ref.kind) {
                HtmlPageKind.PLAYER -> "player"
                HtmlPageKind.RECENT -> "recent"
                HtmlPageKind.LOGIN_DIAGNOSTIC -> "login_response"
                HtmlPageKind.FAILED_RESPONSE -> "failed_response"
                else -> "difficulty_" + when (ref.kind) {
                    HtmlPageKind.BASIC -> 0; HtmlPageKind.ADVANCED -> 1; HtmlPageKind.EXPERT -> 2
                    HtmlPageKind.MASTER -> 3; HtmlPageKind.REMASTER, HtmlPageKind.ULTIMA -> 4
                    HtmlPageKind.UTAGE -> 10; HtmlPageKind.WORLDS_END -> 5
                    else -> error("Not a difficulty")
                }
            } + ".html"
            val target = File(directory, alias)
            if (!target.exists()) {
                val pending = File(directory, "$alias.part")
                try {
                    archives.open(ref).use { input -> pending.outputStream().use { input.copyTo(it, 64 * 1024) } }
                    check(pending.renameTo(target))
                } finally { pending.delete() }
            }
            return JSONObject().put("file", alias).put("sourceFileName", ref.fileName).put("kind", ref.kind.name)
                .put("bytes", ref.bytes).put("sha256", ref.sha256).put("capturedAt", ref.capturedAt.toString())
                .put("contentType", "text/html; charset=UTF-8").put("inspection", captured.evidence.validity.name)
                .put("chartCount", captured.evidence.chartCount).put("scoreCount", captured.evidence.scoreCount)
                .put("status", captured.responseStatus)
        }
        val stage = when (snapshot.stage) {
            CaptureStage.COMPLETED -> "SAVED_FOR_INSPECTION"
            CaptureStage.TIMED_OUT -> "TIMEOUT"
            else -> snapshot.stage.name
        }
        val detail = "${snapshot.stage}：${snapshot.pages.size}/${snapshot.totalPages}" +
            (snapshot.currentPage?.let { " $it" } ?: "")
        val json = JSONObject().put("game", snapshot.request.game.key).put("launchId", snapshot.request.launchId)
            .put("transport", "formal-capture-task-okhttp-4.10").put("directory", directory.absolutePath)
            .put("stage", stage).put("detail", detail).put("pages", JSONArray(snapshot.pages.map { export(it) }))
            .put("proxyEvents", JSONObject(snapshot.proxyEvents)).put("updatedAt", java.time.Instant.now().toString())
            .put("identity", "UNIDENTIFIED").put("failureType", snapshot.failure?.name)
            .put("failureClass", snapshot.failureClass).put("httpStatus", snapshot.httpStatus)
            .put("failureLocation", snapshot.failureLocation)
            .put("protocolIssue", snapshot.protocolIssue?.name)
            .put("httpTrace", JSONArray(snapshot.httpTrace.map { trace -> JSONObject()
                .put("method", trace.method).put("endpoint", trace.endpoint).put("status", trace.status)
                .put("protocol", trace.protocol).put("tlsVersion", trace.tlsVersion)
                .put("redirectEndpoint", trace.redirectEndpoint).put("elapsedMs", trace.elapsedMs) }))
            .put("networkRetries", snapshot.networkRetries)
        snapshot.entryUrl?.let { json.put("entryUrl", it) }
        snapshot.proxyPort?.let { json.put("proxyPort", it) }
        snapshot.businessErrorCode?.let { json.put("netBusinessErrorCode", it) }
        snapshot.loginDiagnostic?.let { json.put("loginDiagnostic", export(it)) }
        snapshot.failedResponse?.let { json.put("failedResponse", export(it)) }
        fun write(file: File) {
            val atomic = AtomicFile(file)
            val stream = atomic.startWrite()
            try { stream.write(json.toString(2).toByteArray(Charsets.UTF_8)); atomic.finishWrite(stream) }
            catch (error: Exception) { atomic.failWrite(stream); throw error }
        }
        write(File(directory, "manifest.json"))
        write(File(root, "status.json"))
        Log.i("NetCaptureTest", "$stage $detail")
    }
}
