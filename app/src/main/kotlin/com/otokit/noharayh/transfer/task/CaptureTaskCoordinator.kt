package com.otokit.noharayh.transfer.task

import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.transfer.net.*
import java.io.Closeable
import java.net.URI
import java.time.Instant
import java.util.Collections
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal interface CaptureChannel : Closeable {
    val entryUrl: String
    val proxyPort: Int
    fun observeHttp(observer: (CaptureLocalHttpExchange) -> Unit) = Unit
}

/** One mutable task owner; transport, archive and Android platform are injected executors. */
internal class CaptureTaskCoordinator(
    private val archives: HtmlCaptures,
    private val openChannel: (authorize: () -> URI, callback: (Result<URI>) -> Unit, event: (String) -> Unit) -> CaptureChannel,
    private val newSession: (() -> NetSession)? = null,
    private val loginDelayMs: Long = 6000,
    private val pageDelayMs: Long = 1200,
    private val newUploadTask: (((Closeable) -> Unit, TransferRecording?) -> BatchUploadTask)? = null,
    private val startRecording: (CaptureRequest, Instant) -> TransferRecording? = { _, _ -> null },
) : CaptureTasks, Closeable {
    private class Job(val request: CaptureRequest) {
        val startedAt: Instant = Instant.now()
        val cancelled = AtomicBoolean(false)
        val callbacks = ArrayBlockingQueue<Result<URI>>(1)
        @Volatile var timeout = false
        var finishing = false
        var recording: TransferRecording? = null
        @Volatile var channel: CaptureChannel? = null
        @Volatile var session: NetSession? = null
        @Volatile var uploadCall: Closeable? = null
        lateinit var thread: Thread
    }
    private val lock = Any()
    private val deliveryLock = Any()
    private val observers = CopyOnWriteArrayList<(CaptureSnapshot) -> Unit>()
    private var state: CaptureSnapshot? = null
    private var job: Job? = null
    private var closed = false

    override fun snapshot(): CaptureSnapshot? = synchronized(lock) { state }

    override fun observe(observer: (CaptureSnapshot) -> Unit): Closeable {
        synchronized(deliveryLock) { observers.add(observer); snapshot()?.let { observer(it) } }
        return Closeable { observers.remove(observer) }
    }

    override fun start(request: CaptureRequest): CaptureStartResult {
        val allowed = GameNetPages.difficulties(request.game)
        if (request.uploadTargets.isNotEmpty() && (newUploadTask == null ||
                request.uploadTargets.any { it.subject.isBlank() } ||
                request.uploadTargets.distinctBy { it.provider }.size != request.uploadTargets.size)) {
            return CaptureStartResult.INVALID_REQUEST
        }
        if (request.launchId.isBlank() || request.difficulties.isEmpty() || request.difficulties.distinct().size != request.difficulties.size ||
            request.difficulties.any { it !in allowed }) return CaptureStartResult.INVALID_REQUEST
        synchronized(lock) {
            if (closed || job != null) return CaptureStartResult.ALREADY_RUNNING
            val fixed = request.copy(difficulties = Collections.unmodifiableList(request.difficulties.toList()),
                uploadTargets = Collections.unmodifiableList(request.uploadTargets.toList()))
            val current = Job(fixed)
            if (!lease.compareAndSet(null, current)) return CaptureStartResult.ALREADY_RUNNING
            job = current
            state = CaptureSnapshot(fixed, current.startedAt, totalPages = fixed.difficulties.size + 2)
            current.recording = try { startRecording(fixed, current.startedAt) } catch (_: Exception) { null }
            current.thread = Thread({ execute(current) }, "NetCaptureTask")
            current.thread.start()
        }
        deliver()
        return CaptureStartResult.ACCEPTED
    }

    override fun cancel(timedOut: Boolean) {
        val current = synchronized(lock) {
            val active = job ?: return
            if (active.finishing) return
            active.timeout = active.timeout || timedOut
            active.cancelled.set(true)
            active
        }
        current.recording?.event("CANCEL", if (timedOut) "任务超时，开始取消" else "用户取消任务")
        runCatching { current.channel?.close() }
        runCatching { current.session?.close() }
        current.uploadCall?.close()
        current.thread.interrupt()
    }

    private fun execute(current: Job) {
        var batch: HtmlBatchRef? = null
        var archiveCompleted = false
        var stage = CaptureStage.COMPLETED
        var failure: CaptureFailure? = null
        var code: String? = null
        var failureClass: String? = null
        var failureLocation: String? = null
        var protocolIssue: CaptureProtocolIssue? = null
        var httpStatus: Int? = null
        try {
            current.recording?.event("PREPARING", "任务已受理，准备采集")
            checkActive(current)
            archives.recoverInterrupted()
            batch = archives.begin(current.request.game, listOf(HtmlPageKind.PLAYER, HtmlPageKind.RECENT) + current.request.difficulties,
                current.request.launchId, current.startedAt)
            current.recording?.bindBatch(batch)
            update(current) { it.copy(batch = batch) }
            val session = (newSession?.invoke() ?: GameNetClient(
                onRetry = { update(current) { it.copy(networkRetries = it.networkRetries + 1) } },
                onTrace = { trace -> update(current) { it.copy(httpTrace = Collections.unmodifiableList((it.httpTrace + trace).takeLast(32))) } },
                onRejectedResponse = { save(current, batch, HtmlPageKind.FAILED_RESPONSE, it) },
                recording = current.recording,
            )).also { current.session = it }
            checkActive(current)
            val channel = openChannel({ session.authorizationUrl(current.request.game.key) }, {
                current.recording?.event(if (it.isSuccess) "CALLBACK" else "AUTHORIZATION",
                    if (it.isSuccess) "收到微信登录回调" else "获取游戏授权入口失败")
                current.callbacks.offer(it)
            }, { event ->
                current.recording?.event("CAPTURE", if (event == "connect") "HTTPS CONNECT 仅作 TLS 透传，不包含微信内部明文" else event)
                update(current) { it.copy(proxyEvents = Collections.unmodifiableMap(it.proxyEvents + (event to ((it.proxyEvents[event] ?: 0) + 1)))) }
            }).also { current.channel = it }
            current.recording?.let { channel.observeHttp(it::recordHttp) }
            checkActive(current)
            update(current) { it.copy(stage = CaptureStage.WAITING_WECHAT, entryUrl = channel.entryUrl, proxyPort = channel.proxyPort) }
            val callback = current.callbacks.take().getOrThrow()
            if (!current.request.keepCallbackCaptureUntilLogin) {
                channel.close(); current.channel = null
            }
            update(current) { it.copy(stage = CaptureStage.LOGIN, entryUrl = null, proxyPort = current.channel?.proxyPort) }
            Thread.sleep(loginDelayMs)
            checkActive(current)
            session.login(callback) { page -> save(current, batch, HtmlPageKind.LOGIN_DIAGNOSTIC, page) }
            current.channel?.close(); current.channel = null
            update(current) { it.copy(proxyPort = null) }
            val gamePages = GameNetPages(current.request.game)
            val plan = listOf(HtmlPageKind.PLAYER, HtmlPageKind.RECENT) + current.request.difficulties
            plan.forEachIndexed { index, kind ->
                checkActive(current)
                update(current) { it.copy(stage = CaptureStage.FETCHING, currentPage = kind) }
                gamePages.prepare(session, kind) { save(current, batch, HtmlPageKind.FAILED_RESPONSE, it) }
                val target = gamePages.uri(kind)
                session.read(target) { response ->
                    if (response.uri.host != target.host || response.uri.rawPath != target.rawPath) {
                        save(current, batch, HtmlPageKind.FAILED_RESPONSE, response)
                        throw NetProtocolException(CaptureProtocolIssue.UNEXPECTED_PAGE_REDIRECT)
                    }
                    if (!response.contentType.contains("html", true)) {
                        save(current, batch, HtmlPageKind.FAILED_RESPONSE, response)
                        throw NetProtocolException(CaptureProtocolIssue.NON_HTML_RESPONSE)
                    }
                    save(current, batch, kind, response)
                }
                if (index < plan.lastIndex) Thread.sleep(pageDelayMs)
            }
            checkActive(current)
            if (current.request.uploadTargets.isNotEmpty()) {
                archives.finish(batch, HtmlBatchStatus.COMPLETED)
                archiveCompleted = true
                session.close()
                current.session = null
                update(current) { it.copy(stage = CaptureStage.UPLOADING, currentPage = null) }
                val uploads = requireNotNull(newUploadTask).invoke({ call ->
                    current.uploadCall = call
                    if (current.cancelled.get()) call.close()
                }, current.recording)
                stage = uploads.run(current.request, archives.read(batch), { checkActive(current) }) { result ->
                    update(current) { state ->
                        val prior = state.uploads.filterNot { it.provider == result.provider && it.page == result.page }
                        state.copy(uploads = Collections.unmodifiableList(prior + result))
                    }
                }
                if (stage == CaptureStage.FAILED) failure = CaptureFailure.UPLOAD
            }
        } catch (error: Exception) {
            current.recording?.event("ERROR", "传分执行异常", mapOf("type" to error.javaClass.name))
            val causes = generateSequence<Throwable>(error) { it.cause }.take(5).toList()
            failureClass = causes.joinToString("/") { it.javaClass.simpleName }
            // Code locations only: exception messages may contain OAuth URLs or cookies.
            failureLocation = causes.asSequence().flatMap { it.stackTrace.asSequence() }
                .firstOrNull { it.className.startsWith("com.otokit.noharayh.") }?.let {
                    "${it.className.removePrefix("com.otokit.noharayh.")}.${it.methodName}:${it.lineNumber}"
                }
            httpStatus = causes.filterIsInstance<NetHttpException>().firstOrNull()?.status
            protocolIssue = causes.filterIsInstance<NetProtocolException>().firstOrNull()?.issue
            stage = if (current.cancelled.get()) { if (current.timeout) CaptureStage.TIMED_OUT else CaptureStage.CANCELLED } else CaptureStage.FAILED
            failure = when (error) {
                is NetBusinessException -> CaptureFailure.BUSINESS.also { code = error.code }
                is InvalidPageException -> CaptureFailure.INVALID_PAGE
                is NetProtocolException -> CaptureFailure.INVALID_PAGE
                is ArchiveException -> CaptureFailure.STORAGE
                is InterruptedException -> CaptureFailure.INTERRUPTED
                is java.io.IOException -> CaptureFailure.NETWORK
                else -> CaptureFailure.PLATFORM
            }
        } finally {
            synchronized(lock) {
                current.finishing = true
                if (current.cancelled.get()) stage = if (current.timeout) CaptureStage.TIMED_OUT else CaptureStage.CANCELLED
            }
            runCatching { current.channel?.close() }; current.channel = null
            runCatching { current.session?.close() }; current.session = null
            // Interrupted threads must still persist their terminal index.
            Thread.interrupted()
            batch?.takeUnless { archiveCompleted }?.let {
                runCatching { archives.finish(it, when (stage) {
                    CaptureStage.COMPLETED -> HtmlBatchStatus.COMPLETED
                    CaptureStage.CANCELLED -> HtmlBatchStatus.CANCELLED
                    CaptureStage.TIMED_OUT -> HtmlBatchStatus.TIMED_OUT
                    else -> HtmlBatchStatus.FAILED
                }) }.onFailure { stage = CaptureStage.FAILED; failure = CaptureFailure.STORAGE }
            }
            synchronized(lock) {
                if (job === current) {
                    state = state!!.copy(stage = stage, failure = failure, businessErrorCode = code, failureClass = failureClass,
                        failureLocation = failureLocation, protocolIssue = protocolIssue, httpStatus = httpStatus, entryUrl = null, proxyPort = null)
                    job = null
                    lease.compareAndSet(current, null)
                }
            }
            deliver()
            current.recording?.event("CLEANUP", "采集通道和网络会话已释放")
            current.recording?.finish(stage.name)
        }
    }

    private fun save(current: Job, batch: HtmlBatchRef, kind: HtmlPageKind, response: NetSession.Page) {
        checkActive(current)
        current.recording?.event("HTML", "开始保存页面", mapOf("page" to kind.name, "status" to response.status.toString()))
        val source = object : java.io.FilterInputStream(response.stream) {
            override fun read(): Int = try { `in`.read() } catch (error: java.io.IOException) { throw NetworkSourceException(error) }
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int = try { `in`.read(buffer, offset, length) }
                catch (error: java.io.IOException) { throw NetworkSourceException(error) }
        }
        val ref = try { archives.save(batch, kind, source) { current.cancelled.get() } }
        catch (error: Exception) {
            if (current.cancelled.get()) throw InterruptedException()
            if (error is NetworkSourceException) throw error
            throw ArchiveException(error)
        }
        val evidence = try { archives.open(ref).use { NetHtmlInspector.inspect(current.request.game, kind, it) { current.cancelled.get() } } }
        catch (error: Exception) { if (current.cancelled.get()) throw InterruptedException(); throw ArchiveException(error) }
        val archivedEvidence = if (response.status == 200 || evidence.validity == HtmlValidity.BUSINESS_ERROR) evidence
            else evidence.copy(validity = HtmlValidity.UNRECOGNIZED)
        try { archives.recordEvidence(ref, archivedEvidence) } catch (error: Exception) { throw ArchiveException(error) }
        current.recording?.event("HTML", "页面保存与校验完成", mapOf("page" to kind.name,
            "file" to ref.fileName, "bytes" to ref.bytes.toString(), "validity" to archivedEvidence.validity.name,
            "chartCount" to archivedEvidence.chartCount.toString(), "scoreCount" to archivedEvidence.scoreCount.toString()))
        val captured = CapturedHtml(ref, evidence, response.status)
        update(current) { state ->
            if (kind == HtmlPageKind.LOGIN_DIAGNOSTIC) state.copy(loginDiagnostic = captured)
            else if (kind == HtmlPageKind.FAILED_RESPONSE) state.copy(failedResponse = captured)
            else state.copy(pages = Collections.unmodifiableList(state.pages + captured))
        }
        if (kind == HtmlPageKind.FAILED_RESPONSE) return
        evidence.businessErrorCode?.let { throw NetBusinessException(it) }
        if (response.status != 200) throw NetHttpException(response.status)
        if (evidence.validity == HtmlValidity.UNRECOGNIZED) throw InvalidPageException()
    }

    private fun checkActive(current: Job) { if (current.cancelled.get() || Thread.currentThread().isInterrupted) throw InterruptedException() }
    private fun update(current: Job, change: (CaptureSnapshot) -> CaptureSnapshot) {
        synchronized(lock) { if (job !== current || current.cancelled.get()) return; state = change(state!!) }
        snapshot()?.let { value -> current.recording?.event(value.stage.name, "任务状态更新", buildMap {
            value.currentPage?.let { put("page", it.name) }
            put("savedPages", value.pages.size.toString())
            put("networkRetries", value.networkRetries.toString())
            value.uploads.lastOrNull()?.let { result ->
                put("provider", result.provider.name); put("uploadPage", result.page.name)
                put("uploadOutcome", result.receipt.outcome.name)
            }
        }) }
        deliver()
    }
    private fun deliver() = synchronized(deliveryLock) { snapshot()?.let { value -> observers.forEach { runCatching { it(value) } } } }
    override fun close() { synchronized(lock) { closed = true }; cancel() }
    private class InvalidPageException : IllegalStateException()
    private class ArchiveException(cause: Exception) : IllegalStateException(cause)
    private class NetworkSourceException(cause: java.io.IOException) : java.io.IOException(cause)

    companion object {
        /** Shared only to exclude simultaneous capture runtimes, never to retain a login/session. */
        private val lease = AtomicReference<Job?>(null)
        fun isActive(taskId: String): Boolean = lease.get()?.request?.launchId == taskId
    }
}
