package com.otokit.noharayh.transfer

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.net.GameNetPages
import com.otokit.noharayh.transfer.task.BatchUploadTask
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class UploadMatrixTest {
    @get:Rule val temp = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)
    private val fish = UploadTarget(OAuthProvider.DIVING_FISH, "fish-fixture")
    private val snow = UploadTarget(OAuthProvider.LXNS, "snow-fixture")
    private val modes = listOf(listOf(fish), listOf(snow), listOf(fish, snow))
    private val store by lazy { FileHtmlCaptures(temp.newFolder()) }

    private fun batch(game: ScoreGame, chosen: List<HtmlPageKind>, id: String): HtmlBatchRecord {
        val kinds = listOf(HtmlPageKind.PLAYER, HtmlPageKind.RECENT) + chosen
        val ref = store.begin(game, kinds, id)
        for (kind in kinds) {
            val body = "${game.key}/${kind.name}"
            val page = store.save(ref, kind, body.byteInputStream()) { false }
            store.recordEvidence(page, HtmlPageEvidence(HtmlValidity.VALID))
        }
        store.finish(ref, HtmlBatchStatus.COMPLETED)
        return store.read(ref)
    }

    // Application interceptor always returns a local fixture before DNS or any socket.
    private fun mockClient(reply: (Request) -> Pair<Int, String>) = OkHttpClient.Builder().addInterceptor {
        val req = it.request()
        val (code, json) = reply(req)
        Response.Builder().request(req).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
            .body(json.toResponseBody()).build()
    }.build()

    @Test fun gamesModesAndEveryNonemptyDifficultySubset() {
        val runtimes = mutableMapOf<String, MutableList<Long>>()
        val totals = mutableMapOf<String, Int>()
        val seen = mutableListOf<Pair<OAuthProvider, String>>()
        val client = mockClient { req ->
            val fishHost = req.url.host == "www.diving-fish.com"
            val target = if (fishHost) fish else snow
            if (!fishHost) assertEquals("maimai.lxns.net", req.url.host)
            assertEquals("POST", req.method)
            assertEquals("Bearer ${target.subject}", req.header("Authorization"))
            val body = requireNotNull(req.body)
            val data = Buffer().also(body::writeTo).readUtf8()
            assertEquals(data.toByteArray().size.toLong(), body.contentLength())
            val game = data.substringBefore('/')
            val fishPath = if (game == "maimai") "maimaidxprober" else "chunithmprober"
            assertEquals(if (fishHost) "/api/$fishPath/player/update_records_html" else "/api/v0/user/$game/player/html",
                req.url.encodedPath)
            seen += target.provider to data
            200 to if (fishHost) """{"message":"更新成功","updates":2,"creates":1}"""
                else """{"success":true,"code":200,"data":[]}"""
        }
        val ledger = temp.newFolder()
        for (game in ScoreGame.entries) {
            val upload = PlatformHtmlUpload(store, { provider, subject, scope ->
                assertEquals(provider.scoreWriteScope(game == ScoreGame.MAIMAI), scope)
                subject
            }, client)
            val difficulties = GameNetPages.difficulties(game)
            assertEquals(6, difficulties.size)
            for (mask in 1 until (1 shl difficulties.size)) {
                val selected = difficulties.filterIndexed { i, _ -> (mask and (1 shl i)) != 0 }
                val id = "${game.key}-$mask"
                val record = batch(game, selected, id)
                for (targets in modes) {
                    val mode = targets.joinToString("+") { it.provider.name }
                    val key = "${game.key}/$mode"
                    val task = BatchUploadTask(File(ledger, mode)) { target, page ->
                        upload.send(target.provider, target.subject, page) {}
                    }
                    seen.clear()
                    val started = SystemClock.elapsedRealtime()
                    val stage = task.run(CaptureRequest(game, selected, id, uploadTargets = targets), record, {}) { result ->
                        val outcome = result.receipt.outcome
                        if (outcome == UploadOutcome.SENDING || outcome == UploadOutcome.UNSUPPORTED) return@run
                        val expected = if (result.provider == fish.provider) UploadReceipt(UploadOutcome.ACCEPTED, 200, 3)
                            else UploadReceipt(UploadOutcome.NO_CHANGES, 200, 0)
                        assertEquals(expected, result.receipt)
                    }
                    val expected = targets.flatMap { target ->
                        (if (target == fish) selected else record.expectedPages).map { target.provider to "${game.key}/${it.name}" }
                    }
                    assertEquals("$key mask=$mask", CaptureStage.COMPLETED, stage)
                    assertEquals("$key mask=$mask", expected, seen)
                    runtimes.getOrPut(key) { mutableListOf() }.add(SystemClock.elapsedRealtime() - started)
                    totals[key] = totals.getOrDefault(key, 0) + seen.size
                }
            }
        }
        for ((key, times) in runtimes) {
            times.sort()
            Log.i("ScoreUploadTest", "$key cases=${times.size} calls=${totals[key]} p50=${times[31]}ms p95=${times[59]}ms max=${times.last()}ms")
        }
    }

    @Test fun responseFieldsAndHttpErrors() {
        val page = batch(ScoreGame.MAIMAI, listOf(HtmlPageKind.EXPERT), "receipts").pages.last()
        var status = 200
        var json = ""
        val upload = PlatformHtmlUpload(store, { _, _, _ -> "fixture" }, mockClient { status to json })
        fun check(target: UploadTarget, http: Int, body: String, outcome: UploadOutcome, count: Int? = null) {
            status = http
            json = body
            val result = upload.send(target.provider, target.subject, page) {}
            Log.i("ScoreUploadTest", "fixture ${target.provider} HTTP=$http body=$body receipt=$result")
            assertEquals(UploadReceipt(outcome, http, count), result)
        }
        check(fish, 200, """{"updates":3,"creates":2}""", UploadOutcome.ACCEPTED, 5)
        check(fish, 200, """{"updates":0,"creates":0}""", UploadOutcome.NO_RECORDS, 0)
        check(fish, 200, """{"status":"error","message":"fixture"}""", UploadOutcome.REJECTED)
        check(fish, 200, """{"message":"fixture"}""", UploadOutcome.UNKNOWN)
        check(snow, 200, """{"success":true,"code":200,"data":[{},{}]}""", UploadOutcome.UPDATED, 2)
        check(snow, 200, """{"success":true,"code":200,"data":[]}""", UploadOutcome.NO_CHANGES, 0)
        check(snow, 200, """{"success":true,"code":200}""", UploadOutcome.ACCEPTED)
        check(snow, 200, """{"success":false,"code":403}""", UploadOutcome.REJECTED)
        check(snow, 404, """{"success":false,"code":404,"message":"player not found"}""", UploadOutcome.PLAYER_NOT_FOUND)
        check(snow, 404, """{"message":"route not found"}""", UploadOutcome.REJECTED)
        check(snow, 404, "not-json", UploadOutcome.REJECTED)
        check(fish, 404, """{"message":"player not found"}""", UploadOutcome.REJECTED)
        check(snow, 400, """{"message":"player not found"}""", UploadOutcome.REJECTED)
        for (target in listOf(fish, snow)) {
            check(target, 200, "not-json", UploadOutcome.UNKNOWN)
            check(target, 400, "{}", UploadOutcome.REJECTED)
            check(target, 401, "{}", UploadOutcome.AUTH_REQUIRED)
            check(target, 403, "{}", UploadOutcome.PERMISSION_DENIED)
            check(target, 429, "{}", UploadOutcome.RATE_LIMITED)
            check(target, 503, "{}", UploadOutcome.UNKNOWN)
        }
    }

    @Test fun fullSizeArchivedPagesMatchTheirDigestsInBothUploads() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val archive = FileHtmlCaptures(File(ctx.filesDir, "score/html"))
        for (game in ScoreGame.entries) {
            val complete = archive.list(game).asSequence().map(archive::read).firstOrNull {
                it.status == HtmlBatchStatus.COMPLETED && it.expectedPages.size == 8
            }
            assumeTrue("No full-size archived fixture for $game", complete != null)
            val batch = requireNotNull(complete)
            var bytes = 0L
            var calls = 0
            val selected = batch.expectedPages.filter { it.isDifficulty }
            val targets = listOf(fish, snow)
            var digest = ""
            val transport = mockClient { request ->
                val body = requireNotNull(request.body)
                val sink = Buffer()
                body.writeTo(sink)
                assertEquals(body.contentLength(), sink.size)
                assertEquals(digest, sink.sha256().hex())
                bytes += sink.size
                calls++
                200 to if (request.url.host == "www.diving-fish.com") """{"updates":0,"creates":0}"""
                    else """{"success":true,"code":200,"data":[]}"""
            }
            val adapter = PlatformHtmlUpload(archive, { _, _, _ -> "fixture" }, transport)
            val task = BatchUploadTask(temp.newFolder()) { target, page ->
                digest = page.sha256
                adapter.send(target.provider, target.subject, page) {}
            }
            val started = SystemClock.elapsedRealtime()
            val stage = task.run(CaptureRequest(game, selected, batch.taskId, uploadTargets = targets), batch, {}, {})
            assertEquals(CaptureStage.COMPLETED, stage)
            assertEquals(14, calls)
            Log.i("ScoreUploadTest", "archive $game calls=$calls bytes=$bytes elapsed=${SystemClock.elapsedRealtime() - started}ms")
        }
    }
}
