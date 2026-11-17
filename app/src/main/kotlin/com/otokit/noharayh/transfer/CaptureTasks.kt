package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.*
import java.io.Closeable

data class CaptureRequest(val game: ScoreGame, val difficulties: List<HtmlPageKind>, val launchId: String)
enum class CaptureStage { PREPARING, WAITING_WECHAT, LOGIN, FETCHING, COMPLETED, FAILED, CANCELLED, TIMED_OUT }
enum class CaptureFailure { NETWORK, BUSINESS, INVALID_PAGE, STORAGE, PLATFORM, INTERRUPTED }
enum class CaptureProtocolIssue { FORM_TOKEN_MISSING, FORM_TOKEN_VALUE_MISSING, UNEXPECTED_PAGE_REDIRECT, NON_HTML_RESPONSE, AUTOMATIC_REPLAY_BLOCKED, REDIRECT_LOOP }
data class CaptureHttpTrace(val method: String, val endpoint: String, val status: Int, val protocol: String,
                            val tlsVersion: String?, val redirectEndpoint: String?, val elapsedMs: Long)
data class CapturedHtml(val page: HtmlPageRef, val evidence: HtmlPageEvidence, val responseStatus: Int)
data class CaptureSnapshot(
    val request: CaptureRequest,
    val stage: CaptureStage = CaptureStage.PREPARING,
    val batch: HtmlBatchRef? = null,
    val pages: List<CapturedHtml> = emptyList(),
    val loginDiagnostic: CapturedHtml? = null,
    val failedResponse: CapturedHtml? = null,
    val httpTrace: List<CaptureHttpTrace> = emptyList(),
    val currentPage: HtmlPageKind? = null,
    val totalPages: Int = 0,
    val entryUrl: String? = null,
    val proxyPort: Int? = null,
    val proxyEvents: Map<String, Int> = emptyMap(),
    val networkRetries: Int = 0,
    val failure: CaptureFailure? = null,
    val businessErrorCode: String? = null,
    val failureClass: String? = null,
    val failureLocation: String? = null,
    val protocolIssue: CaptureProtocolIssue? = null,
    val httpStatus: Int? = null,
) {
    val terminal: Boolean get() = stage in listOf(CaptureStage.COMPLETED, CaptureStage.FAILED, CaptureStage.CANCELLED, CaptureStage.TIMED_OUT)
}
enum class CaptureStartResult { ACCEPTED, ALREADY_RUNNING, INVALID_REQUEST }

/** Observation immediately delivers a snapshot. Activity lifetime does not own the job. */
interface CaptureTasks {
    fun start(request: CaptureRequest): CaptureStartResult
    fun cancel(timedOut: Boolean = false)
    fun snapshot(): CaptureSnapshot?
    fun observe(observer: (CaptureSnapshot) -> Unit): Closeable
}
