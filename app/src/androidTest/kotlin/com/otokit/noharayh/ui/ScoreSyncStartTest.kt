package com.otokit.noharayh.ui

import android.content.Intent
import android.content.ClipboardManager
import android.net.VpnService
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import com.otokit.noharayh.composition.scoreSyncStartState
import com.otokit.noharayh.MainActivity
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.CaptureStartResult
import com.otokit.noharayh.transfer.UploadReports
import com.otokit.noharayh.transfer.UploadTarget
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import com.otokit.noharayh.ui.pages.pageScoreSync.SyncStartStep
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ScoreSyncStartTest : UiComponentTestHost() {
    @Test fun serviceRejectsInvalidRequestThroughTheProductionAcknowledgement() {
        val done = CountDownLatch(1)
        var received: Int? = null
        val receiver = object : ResultReceiver(Handler(Looper.getMainLooper())) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                received = resultCode
                done.countDown()
            }
        }
        // Invalid before opening any VPN channel, contacting NET, or uploading scores.
        main { activity.startForegroundService(NetCaptureService.startIntent(activity,
            CaptureRequest(ScoreGame.MAIMAI, emptyList(), "invalid-request"), receiver)) }
        assertTrue(done.await(5, TimeUnit.SECONDS))
        assertEquals(CaptureStartResult.INVALID_REQUEST.ordinal, received)
    }

    @Test fun activityRecreationKeepsPendingPermissionWithoutRestarting() {
        lateinit var state: com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncStartState
        val monitor = instrumentation.addMonitor(MainActivity::class.java.name, null, false)
        try {
            main {
                state = scoreSyncStartState(activity)
                state.start(CaptureRequest(ScoreGame.CHUNITHM, emptyList(), "permission-test"))
                state.advance("permission-test", SyncStartStep.PERMISSION_RESULT)
                activity.recreate()
            }
            activity = monitor.waitForActivityWithTimeout(5_000) ?: error("Activity was not recreated")
            instrumentation.waitForIdleSync()
            main {
                val reopened = scoreSyncStartState(android.view.ContextThemeWrapper(activity, 0))
                assertSame(state, reopened)
                assertEquals(SyncStartStep.PERMISSION_RESULT, reopened.pending!!.step)
                state.finish("permission-test")
            }
        } finally { instrumentation.removeMonitor(monitor) }
    }

    @Test fun readyChannelCopiesEntryAndHandsOffToWechatWithoutReplayingOnReturn() {
        assumeTrue(android.os.Build.VERSION.SDK_INT >= 29)
        assumeTrue(VpnService.prepare(activity) == null)
        assumeTrue(activity.packageManager.getLaunchIntentForPackage("com.tencent.mm") != null)
        val id = java.util.UUID.randomUUID().toString()
        val game = ScoreGame.MAIMAI
        val done = CountDownLatch(1)
        val stopped = CountDownLatch(1)
        lateinit var state: com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncStartState
        var subscription: java.io.Closeable? = null
        var reports: java.io.Closeable? = null
        var previousClip: android.content.ClipData? = null
        try {
            main {
                state = scoreSyncStartState(activity)
                previousClip = activity.getSystemService(ClipboardManager::class.java).primaryClip
                state.start(CaptureRequest(game, listOf(HtmlPageKind.MASTER), id,
                    uploadTargets = listOf(UploadTarget(OAuthProvider.LXNS, "test-no-upload"))))
                subscription = state.observe { if (state.pending == null) done.countDown() }
                reports = UploadReports.observe {
                    if (UploadReports.latest(game)?.let { it.taskId == id && it.terminal } == true) stopped.countDown()
                }
            }
            assertTrue("No WeChat handoff", done.await(8, TimeUnit.SECONDS))
            main {
                assertNull(state.problem(game))
                val report = UploadReports.latest(game)!!
                assertEquals(id, report.taskId)
                assertFalse(report.terminal)
                assertTrue(report.log.any { (it.event as? com.otokit.noharayh.transfer.TransferEvent.Stage)
                    ?.stage == com.otokit.noharayh.transfer.CaptureStage.WAITING_WECHAT })
                activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            instrumentation.waitForIdleSync()
            main {
                val entry = activity.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.text?.toString()
                assertTrue("Entry was not copied", entry?.startsWith("http://127.0.0.2:8284/") == true)
                assertNull(state.pending)
            }
        } finally {
            main {
                subscription?.close()
                if (UploadReports.latest(game)?.taskId == id) {
                    activity.startService(Intent(activity, NetCaptureService::class.java).setAction("stop"))
                }
                activity.startActivity(Intent(activity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            }
            stopped.await(5, TimeUnit.SECONDS)
            instrumentation.waitForIdleSync()
            main {
                reports?.close()
                val clipboard = activity.getSystemService(ClipboardManager::class.java)
                previousClip?.let(clipboard::setPrimaryClip) ?: clipboard.clearPrimaryClip()
            }
        }
    }
}
