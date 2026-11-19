package com.otokit.noharayh.ui

import android.widget.TextView
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.core.view.allViews
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPage
import com.otokit.noharayh.ui.pages.pageScoreSync.styledUploadReport
import com.otokit.noharayh.ui.kit.pageScoreSync.ScoreSyncLogColors
import com.otokit.noharayh.ui.kit.pageScoreSync.ScoreSyncPanel
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ScoreSyncTaskResponseTest : UiComponentTestHost() {
    @Test fun multilineReceiptsKeepFollowingEventColors() {
        val at = Instant.now()
        val events = listOf(
            TransferEvent.Stage(CaptureStage.WAITING_WECHAT, null, null),
            TransferEvent.Saved(HtmlPageKind.BASIC, 1, 8),
            TransferEvent.Upload(UploadPageResult(OAuthProvider.DIVING_FISH, HtmlPageKind.BASIC,
                UploadReceipt(UploadOutcome.ACCEPTED, 200, 5))),
            TransferEvent.Upload(UploadPageResult(OAuthProvider.LXNS, HtmlPageKind.BASIC,
                UploadReceipt(UploadOutcome.NO_CHANGES, 200, 0))),
            TransferEvent.Stage(CaptureStage.COMPLETED, null, null),
        )
        val text = styledUploadReport(UploadReport("fixture", at, true, listOf(HtmlPageKind.BASIC),
            OAuthProvider.entries.toList(), events.map { TransferLogLine(at, it) })) as Spanned
        assertTrue(text.toString().startsWith("[SYSTEM]等待游戏授权\n[DOWNLOAD][1/8]已校验并保存"))
        assertTrue(text.contains("水鱼·Basic·已接受[200]\n[UPLOAD]平台新增成绩5条"))
        assertTrue(text.contains("落雪·Basic·已接受[200]\n[UPLOAD]平台报告无新增成绩"))
        for (value in listOf("[UPLOAD]平台新增成绩5条", "[UPLOAD]平台报告无新增成绩", "[SYSTEM]传分完成")) {
            val start = text.toString().indexOf(value)
            val colors = text.getSpans(start, start + value.length, ForegroundColorSpan::class.java)
            assertTrue("Missing success color on $value", colors.any { it.foregroundColor == ScoreSyncLogColors.SUCCESS })
        }
        val completion = text.toString().lastIndexOf("[SYSTEM]")
        assertTrue(text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            .all { text.getSpanStart(it) >= completion || text.getSpanEnd(it) <= completion })
    }

    @Test fun runningTaskDisablesStartAndReceiptsSurvivePageRecreation() = main {
        val id = UUID.randomUUID().toString()
        val at = Instant.now()
        val game = ScoreGame.MAIMAI
        val provider = OAuthProvider.DIVING_FISH
        val kind = HtmlPageKind.MASTER
        val request = CaptureRequest(game, listOf(kind), id, uploadTargets = listOf(UploadTarget(provider, "private-account")))
        val base = CaptureSnapshot(request, at, totalPages = 3)
        val pageRef = HtmlPageRef(HtmlBatchRef("fixture", game, at), kind, "private-file", 1, "fixture", at)
        val saved = CapturedHtml(pageRef, HtmlPageEvidence(HtmlValidity.VALID), 200)
        val frame = GameFrame(true, 0f, GamePhase.Idle, 1200, 1200, 800, true)
        fun publish(snapshot: CaptureSnapshot) = UploadReports.publish(snapshot, at)
        fun open() = ScoreSyncPage.create(activity) { false }.also {
            host.addView(it.view)
            it.updateGameFrame(frame)
        }
        var page = open()
        // 只检查左侧舞萌面板，中二也可能保留上一次任务的日志。
        fun log() = page.view.allViews.filterIsInstance<ScoreSyncPanel>().first()
            .allViews.filterIsInstance<TextView>().map { it.text.toString() }
            .single { it.contains("[SYSTEM]") }
        try {
            publish(base)
            publish(base.copy(stage = CaptureStage.FETCHING, pages = listOf(saved.copy(responseStatus = 403))))
            assertFalse(log().contains("已校验并保存"))
            val result = UploadPageResult(provider, kind, UploadReceipt(UploadOutcome.ACCEPTED, 200, 5))
            val upload = base.copy(stage = CaptureStage.UPLOADING, pages = listOf(saved), uploads = listOf(result))
            publish(upload)
            publish(upload)
            val before = log()
            assertEquals(1, before.lines().count { it == "[UPLOAD]平台新增成绩5条" })
            assertTrue(before.contains("[DOWNLOAD][1/3]"))
            assertFalse(before.contains("private-"))
            val starts = page.view.allViews.filter { it.contentDescription == "开始传分" }.toList()
            assertEquals(2, starts.size)
            assertTrue(starts.none { it.isEnabled })
            assertFalse(page.view.allViews.any { it.contentDescription == "取消传分" })
            assertFalse(log().contains("任务已取消"))
            page.dispose()
            host.removeView(page.view)
            page = open()
            assertEquals(before, log())
            publish(upload.copy(stage = CaptureStage.COMPLETED))
            publish(upload)
            // 未具备写权限的设备还会在日志末尾追加授权提示，完成回执仍应保留。
            assertTrue(log().lines().contains("[SYSTEM]传分完成"))
            assertFalse(page.view.allViews.any { it.contentDescription == "取消传分" })
        } finally {
            publish(base.copy(stage = CaptureStage.CANCELLED))
            page.dispose()
            host.removeView(page.view)
        }
    }
}
