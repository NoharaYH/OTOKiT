package com.otokit.noharayh.transfer

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.core.view.allViews
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.composition.oauthConnections
import com.otokit.noharayh.composition.scoreSyncStartState
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import com.otokit.noharayh.ui.UiComponentTestHost
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPage
import com.otokit.noharayh.ui.pages.pageScoreSync.formatUploadReport
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.Closeable
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Opt-in real UI → VPN → WeChat → archive → platform upload; never supplies login callbacks. */
class LiveScoreSyncTest : UiComponentTestHost() {
    @Test fun selectedCase() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveTransfer") == "yes")
        val game = ScoreGame.valueOf(requireNotNull(args.getString("game")))
        val mode = requireNotNull(args.getString("mode"))
        val indices = requireNotNull(args.getString("difficulties")).split(',').map(String::toInt).toSet()
        val providers = when (mode) {
            "fish" -> listOf(OAuthProvider.DIVING_FISH)
            "snow" -> listOf(OAuthProvider.LXNS)
            "both" -> OAuthProvider.entries.toList()
            else -> error("Unknown mode")
        }
        val authReady = CountDownLatch(1)
        lateinit var authSubscription: Closeable
        main {
            val auth = oauthConnections(activity)
            authSubscription = auth.observe { if (providers.all { auth.account(it) != null }) authReady.countDown() }
        }
        try { assertTrue("Platform authorization missing", authReady.await(5, TimeUnit.SECONDS)) }
        finally { main { authSubscription.close() } }
        val done = CountDownLatch(1)
        val snapshotDone = CountDownLatch(1)
        var result: UploadReport? = null
        val detail = java.util.concurrent.atomic.AtomicReference<CaptureSnapshot>()
        var lastTrace: CaptureHttpTrace? = null
        var observation: Closeable? = null
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                observation = (binder as NetCaptureService.CaptureBinder).observe {
                    detail.set(it)
                    it.httpTrace.lastOrNull()?.takeIf { trace -> trace != lastTrace }?.let { trace ->
                        lastTrace = trace
                        instrumentation.sendStatus(2, Bundle().apply {
                            putString("http", "${trace.method} ${trace.endpoint} HTTP=${trace.status} elapsed=${trace.elapsedMs}ms")
                        })
                    }
                    if (it.terminal) snapshotDone.countDown()
                }
            }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        var bound = false
        lateinit var id: String
        lateinit var reportSubscription: Closeable
        lateinit var page: com.otokit.noharayh.ui.kit.common.PageContent
        main {
            page = ScoreSyncPage.create(activity) { false }
            host.addView(page.view, android.widget.FrameLayout.LayoutParams(-1, -1))
            page.updateGameFrame(GameFrame(game == ScoreGame.MAIMAI, 0f, GamePhase.Idle, host.width, host.width, host.height, true))
            val labels = if (game == ScoreGame.MAIMAI) listOf("Basic", "Advanced", "Expert", "Master", "Re:Master", "U·TA·GE")
                else listOf("BASIC", "ADVANCED", "EXPERT", "MASTER", "ULTIMA", "WORLD'S END")
            labels.forEachIndexed { index, label ->
                if (index !in indices) page.view.allViews.single { it.contentDescription == label }.performClick()
            }
            val modeLabel = when (mode) { "fish" -> "水鱼"; "snow" -> "落雪"; else -> "双平台" }
            page.view.allViews.single { it.isShown && it.contentDescription == modeLabel }.performClick()
            val button = page.view.allViews.single { it.isShown && it.contentDescription == "开始传分" }
            assertTrue("Start is disabled", button.isEnabled)
            button.performClick()
            id = scoreSyncStartState(activity).pending!!.request.launchId
            reportSubscription = UploadReports.observe {
                val current = UploadReports.latest(game)?.takeIf { it.taskId == id } ?: return@observe
                result = current
                instrumentation.sendStatus(2, Bundle().apply { putString("progress", "$game/$mode logs=${current.log.size} terminal=${current.terminal}") })
                if (current.terminal) done.countDown()
            }
        }
        // The production launcher starts the service asynchronously; wait for its first report before binding.
        val ready = CountDownLatch(1)
        var readySubscription: Closeable? = null
        main { readySubscription = UploadReports.observe { if (UploadReports.latest(game)?.taskId == id) ready.countDown() } }
        try {
            assertTrue("Service did not start", ready.await(8, TimeUnit.SECONDS))
            main { readySubscription?.close(); bound = activity.bindService(Intent(activity, NetCaptureService::class.java), connection, 0) }
            val completed = done.await(180, TimeUnit.SECONDS)
            if (completed) assertTrue("Terminal snapshot missing", snapshotDone.await(5, TimeUnit.SECONDS))
            val report = requireNotNull(result)
            val folder = File(activity.filesDir, "transfer-live-tests/$id").apply { mkdirs() }
            File(folder, "report.txt").writeText(formatUploadReport(report))
            val snapshot = detail.get()?.takeIf { it.request.launchId == id }
            File(folder, "diagnostic.json").writeText(JSONObject().apply {
                put("game", game.name); put("mode", mode); put("indices", JSONArray(indices.sorted()))
                put("stage", snapshot?.stage?.name); put("failure", snapshot?.failure?.name)
                put("httpStatus", snapshot?.httpStatus); put("failureClass", snapshot?.failureClass)
                put("batch", snapshot?.batch?.id); put("keepCallbackCaptureUntilLogin", snapshot?.request?.keepCallbackCaptureUntilLogin)
                put("trace", JSONArray(snapshot?.httpTrace.orEmpty().map { JSONObject()
                    .put("endpoint", it.endpoint).put("status", it.status).put("elapsedMs", it.elapsedMs) }))
            }.toString(2))
            instrumentation.sendStatus(2, Bundle().apply {
                putString("report", formatUploadReport(report)); putString("evidence", "transfer-live-tests/$id")
            })
            assertTrue("Task did not finish; diagnostic snapshot saved", completed)
            val knownSpecialFailure = 5 in indices && OAuthProvider.DIVING_FISH in providers
            assertEquals("See task log", if (knownSpecialFailure) CaptureStage.PARTIAL else CaptureStage.COMPLETED, snapshot?.stage)
            val expectedKinds = com.otokit.noharayh.transfer.net.GameNetPages.difficulties(game)
                .filterIndexed { index, _ -> index in indices }
            assertEquals(expectedKinds, snapshot!!.request.difficulties)
            assertEquals(providers, snapshot.request.uploadTargets.map { it.provider })
            assertEquals(listOf(com.otokit.noharayh.score.HtmlPageKind.PLAYER, com.otokit.noharayh.score.HtmlPageKind.RECENT) + expectedKinds,
                snapshot.pages.map { it.page.kind })
            snapshot.uploads.forEach { pageResult ->
                if (pageResult.provider == OAuthProvider.DIVING_FISH && pageResult.page in
                    listOf(com.otokit.noharayh.score.HtmlPageKind.UTAGE, com.otokit.noharayh.score.HtmlPageKind.WORLDS_END)) {
                    assertEquals(UploadReceipt(UploadOutcome.REJECTED, 400), pageResult.receipt)
                } else assertTrue("Unexpected receipt: $pageResult",
                    pageResult.receipt.accepted || pageResult.receipt.outcome == UploadOutcome.UNSUPPORTED)
            }
        } finally {
            main {
                if (detail.get()?.let { it.request.launchId == id && !it.terminal } == true) {
                    activity.startService(Intent(activity, NetCaptureService::class.java).setAction("stop"))
                }
            }
            snapshotDone.await(5, TimeUnit.SECONDS)
            main {
                readySubscription?.close(); reportSubscription.close(); observation?.close()
                if (bound) activity.unbindService(connection)
                page.dispose(); host.removeView(page.view)
            }
        }
    }
}
