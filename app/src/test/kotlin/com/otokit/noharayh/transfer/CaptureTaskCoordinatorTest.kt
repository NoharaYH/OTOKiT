package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.net.*
import com.otokit.noharayh.transfer.task.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URI
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.Interceptor

class CaptureTaskCoordinatorTest {
    @get:Rule val temp = TemporaryFolder()
    private val uri = URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=test&state=test")
    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) { if (System.nanoTime() > deadline) fail("Task did not reach expected state"); Thread.sleep(5) }
    }
    private fun channel(closes: AtomicInteger) = object : CaptureChannel {
        private val closed = AtomicBoolean(false)
        override val entryUrl = "http://127.0.0.2:8284/test"
        override val proxyPort = 12345
        override fun close() { if (closed.compareAndSet(false, true)) closes.incrementAndGet() }
    }

    @Test fun authorizationFailurePreservesCauseCleansUpAndAllowsAnotherTask() {
        for (error in listOf(java.io.IOException("private-code"), NetHttpException(503), NetHttpException(200))) {
            val closes = AtomicInteger()
            val sessions = CopyOnWriteArrayList<FakeSession>()
            val attempts = AtomicInteger()
            val states = CopyOnWriteArrayList<CaptureStage>()
            CaptureTaskCoordinator(FileHtmlCaptures(temp.newFolder()), { _, callback, _ ->
                callback(if (attempts.incrementAndGet() == 1) Result.failure(error) else Result.success(uri))
                channel(closes)
            }, { FakeSession().also(sessions::add) }, 0, 0).use { tasks ->
                tasks.observe { states.add(it.stage) }.use {
                    val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "entry-failed")
                    assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request))
                    await { tasks.snapshot()?.terminal == true }
                    val failed = tasks.snapshot()!!
                    assertEquals(CaptureStage.FAILED, failed.stage)
                    assertEquals(CaptureFailure.NETWORK, failed.failure)
                    assertEquals((error as? NetHttpException)?.status, failed.httpStatus)
                    assertEquals(error.javaClass.simpleName, failed.failureClass)
                    assertFalse(states.contains(CaptureStage.LOGIN))
                    assertNull(failed.entryUrl)
                    assertNull(failed.proxyPort)
                    assertTrue(sessions.single().closed)
                    assertEquals(0, sessions.single().reads)
                    assertEquals(1, closes.get())
                    assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request.copy(launchId = "retry")))
                    await { tasks.snapshot()?.terminal == true }
                    assertEquals(CaptureStage.COMPLETED, tasks.snapshot()!!.stage)
                    assertEquals(2, closes.get())
                }
            }
        }
    }

    @Test fun lateAuthorizationFailureDoesNotReplaceCancellation() {
        val result = AtomicReference<(Result<URI>) -> Unit>()
        val closes = AtomicInteger()
        val session = FakeSession()
        CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, callback, _ ->
            result.set(callback)
            channel(closes)
        }, { session }, 0, 0).use { tasks ->
            tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "cancel-entry"))
            await { tasks.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
            tasks.cancel()
            await { tasks.snapshot()?.terminal == true }
            result.get().invoke(Result.failure(java.io.IOException("late")))
            assertEquals(CaptureStage.CANCELLED, tasks.snapshot()!!.stage)
            assertEquals(1, closes.get())
            assertTrue(session.closed)
            assertEquals(0, session.reads)
        }
    }

    @Test fun onlyAcceptedTasksStartRecordingAndCancellationAlwaysFinishesIt() {
        val starts = AtomicInteger()
        val bound = AtomicReference<HtmlBatchRef>()
        val finished = AtomicReference<String>()
        val events = CopyOnWriteArrayList<String>()
        CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, _, _ -> channel(AtomicInteger()) },
            { FakeSession() }, 0, 0, startRecording = { request, _ ->
                assertTrue(request.saveSyncLog)
                starts.incrementAndGet()
                TransferRecording({ stage, message, _ -> events += "$stage:$message" },
                    { Interceptor { chain -> chain.proceed(chain.request()) } }, bound::set, finished::set, {})
            }).use { tasks ->
            val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "logged", saveSyncLog = true)
            assertEquals(CaptureStartResult.INVALID_REQUEST, tasks.start(request.copy(difficulties = emptyList())))
            assertEquals(0, starts.get())
            assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request))
            await { tasks.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
            assertEquals(1, starts.get())
            assertEquals(tasks.snapshot()!!.batch, bound.get())
            assertEquals(CaptureStartResult.ALREADY_RUNNING, tasks.start(request.copy(launchId = "duplicate")))
            assertEquals(1, starts.get())
            tasks.cancel()
            await { finished.get() != null }
            assertEquals(CaptureStage.CANCELLED.name, finished.get())
            assertTrue(events.any { it.startsWith("CLEANUP:") })
        }
    }

    @Test fun taskSavesEvidenceAndKeepsObserversIndependentOfLifetime() {
        val closes = AtomicInteger()
        val store = FileHtmlCaptures(temp.root)
        val session = FakeSession()
        CaptureTaskCoordinator(store, { _, callback, _ -> callback(Result.success(uri)); channel(closes) }, { session }, 0, 0).use { tasks ->
            val kinds = mutableListOf(HtmlPageKind.MASTER)
            val request = CaptureRequest(ScoreGame.MAIMAI, kinds, "one")
            assertEquals(CaptureStartResult.ACCEPTED, tasks.start(request))
            kinds.clear()
            await { tasks.snapshot()?.terminal == true }
            val result = tasks.snapshot()!!
            assertEquals(CaptureStage.COMPLETED, result.stage)
            assertEquals(3, result.pages.size)
            assertEquals(result.startedAt, result.batch!!.startedAt)
            assertEquals(1, result.pages.last().evidence.scoreCount)
            assertEquals(1, closes.get())
            assertTrue(session.closed)
            var observed: CaptureSnapshot? = null
            tasks.observe { observed = it }.close()
            assertEquals(result, observed)
            assertEquals(listOf(HtmlPageKind.MASTER), result.request.difficulties)
        }
    }

    @Test fun duplicateRuntimesAreRejectedAndCancellationReleasesTheLease() {
        val closes = AtomicInteger()
        val first = CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, _, _ -> channel(closes) }, { FakeSession() }, 0, 0)
        val second = CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, _, _ -> channel(closes) }, { FakeSession() }, 0, 0)
        try {
            val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "one")
            assertEquals(CaptureStartResult.ACCEPTED, first.start(request))
            await { first.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
            assertEquals(CaptureStartResult.ALREADY_RUNNING, first.start(request))
            assertEquals(CaptureStartResult.ALREADY_RUNNING, second.start(request))
            first.cancel()
            await { first.snapshot()?.terminal == true }
            assertEquals(CaptureStage.CANCELLED, first.snapshot()!!.stage)
            assertEquals(CaptureStartResult.ACCEPTED, second.start(request.copy(launchId = "two")))
            await { second.snapshot()?.stage == CaptureStage.WAITING_WECHAT }
            second.cancel(timedOut = true)
            await { second.snapshot()?.terminal == true }
            assertEquals(CaptureStage.TIMED_OUT, second.snapshot()!!.stage)
        } finally { first.close(); second.close() }
    }

    @Test fun businessErrorKeepsDiagnosticAndNeverFetchesScorePages() {
        val session = FakeSession(businessError = true)
        CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, callback, _ -> callback(Result.success(uri)); channel(AtomicInteger()) }, { session }, 0, 0).use { tasks ->
            tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "business"))
            await { tasks.snapshot()?.terminal == true }
            val result = tasks.snapshot()!!
            assertEquals(CaptureStage.FAILED, result.stage)
            assertEquals(CaptureFailure.BUSINESS, result.failure)
            assertEquals("100106", result.businessErrorCode)
            assertNotNull(result.loginDiagnostic)
            assertTrue(result.pages.isEmpty())
            assertEquals(0, session.reads)
        }
    }

    @Test fun failureLocationDoesNotExposeExceptionMessageOrLoginMaterial() {
        val session = FakeSession(failRead = true)
        CaptureTaskCoordinator(FileHtmlCaptures(temp.root), { _, callback, _ -> callback(Result.success(uri)); channel(AtomicInteger()) }, { session }, 0, 0).use { tasks ->
            tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "failure"))
            await { tasks.snapshot()?.terminal == true }
            val result = tasks.snapshot()!!
            assertEquals(CaptureStage.FAILED, result.stage)
            assertEquals("IllegalStateException", result.failureClass)
            assertTrue(result.failureLocation!!.contains("FakeSession.read:"))
            assertFalse(result.failureLocation.contains("private-code"))
            assertFalse(result.failureLocation.contains("https://"))
        }
    }

    @Test fun captureCloseTimingFollowsTaskOptionAndFailureAlwaysCleansUp() {
        for (keep in listOf(false, true)) for (failLogin in listOf(false, true)) {
            val closes = AtomicInteger()
            var closedAtLogin = -1
            var closedAtRead = -1
            var portAtLogin: Int? = null
            val store = FileHtmlCaptures(temp.newFolder())
            lateinit var tasks: CaptureTaskCoordinator
            val session = FakeSession(onLogin = {
                closedAtLogin = closes.get()
                portAtLogin = tasks.snapshot()!!.proxyPort
                if (failLogin) throw java.io.IOException("Login failed")
            }, onRead = { closedAtRead = closes.get() })
            tasks = CaptureTaskCoordinator(store, { _, callback, _ -> callback(Result.success(uri)); channel(closes) }, { session }, 0, 0)
            tasks.use {
                tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "timing", keep))
                await { tasks.snapshot()?.terminal == true }
                assertEquals(if (keep) 0 else 1, closedAtLogin)
                assertEquals(if (keep) 12345 else null, portAtLogin)
                assertEquals(if (failLogin) -1 else 1, closedAtRead)
                assertEquals(if (failLogin) CaptureStage.FAILED else CaptureStage.COMPLETED, tasks.snapshot()!!.stage)
                assertEquals(1, closes.get())
                assertNull(tasks.snapshot()!!.proxyPort)
                assertNull(tasks.snapshot()!!.entryUrl)
                assertTrue(session.closed)
            }
        }
    }

    @Test fun cancellationAndTimeoutCloseRetainedChannelDuringLogin() {
        for (timedOut in listOf(false, true)) {
            val closes = AtomicInteger()
            val entered = CountDownLatch(1)
            val session = FakeSession(onLogin = { entered.countDown(); CountDownLatch(1).await() })
            CaptureTaskCoordinator(FileHtmlCaptures(temp.newFolder()),
                { _, callback, _ -> callback(Result.success(uri)); channel(closes) }, { session }, 0, 0).use { tasks ->
                tasks.start(CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "cancel", true))
                assertTrue(entered.await(3, TimeUnit.SECONDS))
                assertEquals(0, closes.get())
                assertNull(tasks.snapshot()!!.entryUrl)
                tasks.cancel(timedOut)
                await { tasks.snapshot()?.terminal == true }
                assertEquals(if (timedOut) CaptureStage.TIMED_OUT else CaptureStage.CANCELLED, tasks.snapshot()!!.stage)
                assertEquals(1, closes.get())
                assertEquals(0, session.reads)
                assertTrue(session.closed)
            }
        }
    }

    private class FakeSession(
        private val businessError: Boolean = false,
        private val failRead: Boolean = false,
        private val onLogin: () -> Unit = {},
        private val onRead: () -> Unit = {},
    ) : NetSession {
        var closed = false
        var reads = 0
        override fun authorizationUrl(game: String) = URI("https://open.weixin.qq.com/")
        override fun login(callback: URI, saveDiagnostic: (NetSession.Page) -> Unit) {
            onLogin()
            saveDiagnostic(page(URI("https://maimai.wahlap.com/maimai-mobile/home/"),
                if (businessError) "<html>错误码：100106</html>" else "<html>maimai-mobile</html>"))
        }
        override fun <T> read(uri: URI, post: String?, consume: (NetSession.Page) -> T): T {
            onRead()
            if (failRead) error("https://example.invalid/?code=private-code")
            reads++
            val body = when {
                uri.path.contains("userFriendCode") -> "<html>friend_code_icon</html>"
                uri.path.endsWith("/record/") -> "<html>playlog</html>"
                else -> "<html>maimai-mobile<div class=\"music_name_block\">title</div><div class=\"music_score_block w_112\">100.0000%</div></html>"
            }
            return consume(page(uri, body))
        }
        private fun page(uri: URI, body: String) = NetSession.Page(uri, "text/html", body.byteInputStream(), 200)
        override fun close() { closed = true }
    }
}
