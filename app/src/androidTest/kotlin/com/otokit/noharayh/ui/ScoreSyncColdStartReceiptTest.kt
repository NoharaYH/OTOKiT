package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import com.otokit.noharayh.ui.kit.pageScoreSync.ScoreSyncPanel
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.UUID

class ScoreSyncColdStartReceiptTest : UiComponentTestHost() {
    @get:Rule val temp = TemporaryFolder(instrumentation.targetContext.cacheDir)
    private val guidance = "您的落雪平台好像暂无分数，为保证落雪平台能正常上传成绩，请先使用落雪官网的代理上传上传一次。"

    @Test fun emptyBody400RemainsAnOrdinaryRejectionAfterColdStart() = receipt(
        400, "invalid request body", UploadOutcome.REJECTED, "score-sync-empty-body-logframe.png")

    @Test fun controlledPlayerNotFound404ReachesTheExistingColdStartLogFrame() = receipt(
        404, "player not found", UploadOutcome.PLAYER_NOT_FOUND, "score-sync-player-not-found-logframe.png")

    private fun receipt(status: Int, message: String, outcome: UploadOutcome, screenshot: String) {
        main { host.visibility = View.GONE }
        var panelIndex = 0
        lateinit var game: ScoreGame
        awaitMain {
            val panels = activity.window.decorView.allViews.filterIsInstance<ScoreSyncPanel>().toList()
            panelIndex = panels.indexOfFirst { it.visibility == View.VISIBLE && it.isShown && it.width > 0 }
            panels.size == 2 && panelIndex >= 0
        }
        main {
            game = if (panelIndex == 0) ScoreGame.MAIMAI else ScoreGame.CHUNITHM
            val panel = activity.window.decorView.allViews.filterIsInstance<ScoreSyncPanel>().elementAt(panelIndex)
            panel.allViews.single { it.contentDescription == "落雪" }.performClick()
        }
        val id = UUID.randomUUID().toString()
        val at = Instant.now()
        val store = FileHtmlCaptures(temp.newFolder())
        val batch = store.begin(game, listOf(HtmlPageKind.PLAYER), id, at)
        val page = store.save(batch, HtmlPageKind.PLAYER, ByteArray(0).inputStream()) { false }
        var calls = 0
        // 本地应用拦截器直接返回受控回执，不经过 DNS、socket 或真实账号凭据。
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            calls++
            assertEquals("POST", request.method)
            assertEquals("https://maimai.lxns.net/api/v0/user/${game.key}/player/html", request.url.toString())
            val body = requireNotNull(request.body)
            assertEquals(0L, body.contentLength())
            assertEquals(0L, Buffer().also(body::writeTo).size)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .body("""{"success":false,"code":$status,"message":"$message"}""".toResponseBody()).build()
        }.build()
        val result = PlatformHtmlUpload(store, { _, _, _ -> "fixture-token" }, client)
            .send(OAuthProvider.LXNS, "fixture-subject", page) {}
        assertEquals(UploadReceipt(outcome, status), result)
        assertEquals(1, calls)
        val request = CaptureRequest(game, listOf(HtmlPageKind.PLAYER), id,
            uploadTargets = listOf(UploadTarget(OAuthProvider.LXNS, "fixture-subject")))
        main { UploadReports.publish(CaptureSnapshot(request, at, stage = CaptureStage.FAILED,
            failure = CaptureFailure.UPLOAD, uploads = listOf(UploadPageResult(OAuthProvider.LXNS, HtmlPageKind.PLAYER, result))), at) }
        instrumentation.waitForIdleSync()
        main {
            assertEquals(id, UploadReports.latest(game)?.taskId)
            Log.i("ColdStartReceipt", "game=$game HTTP=$status bytes=${page.bytes}")
        }
        lateinit var log: TextView
        awaitMain {
            val current = activity.window.decorView.allViews.filterIsInstance<ScoreSyncPanel>().elementAtOrNull(panelIndex)
                ?: return@awaitMain false
            if (!current.isShown || current.visibility != View.VISIBLE || current.width == 0) return@awaitMain false
            log = current.allViews.filterIsInstance<TextView>().firstOrNull {
                it.text.contains("[UPLOAD]落雪·玩家资料·平台拒绝上传[$status]")
            } ?: return@awaitMain false
            log.layout != null && log.width > 0 && log.height > 0 && !log.isLayoutRequested
        }
        main { assertEquals(outcome == UploadOutcome.PLAYER_NOT_FOUND, log.text.contains(guidance)) }
        if (InstrumentationRegistry.getArguments().getString("coldStartScreenshots") == "true") {
            instrumentation.waitForIdleSync()
            instrumentation.uiAutomation.waitForIdle(500, 3000)
            val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
            try { File(activity.cacheDir, screenshot).outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
        }
    }

    private fun awaitMain(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("The existing homepage LogFrame did not receive the receipt")
    }
}
