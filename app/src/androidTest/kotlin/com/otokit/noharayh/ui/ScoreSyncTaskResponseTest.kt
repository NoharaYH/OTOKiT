package com.otokit.noharayh.ui

import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.*
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.ui.animation.common.GameFrame
import com.otokit.noharayh.ui.animation.common.GamePhase
import com.otokit.noharayh.ui.pages.pageScoreSync.ScoreSyncPage
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.util.UUID

class ScoreSyncTaskResponseTest : UiComponentTestHost() {
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
        fun log() = page.view.allViews.filterIsInstance<TextView>().map { it.text.toString() }
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
            assertEquals(1, before.lines().count { it.contains("写入 5 条") })
            assertTrue(before.contains("1/3"))
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
            assertTrue(log().lines().contains("[SYSTEM] 传分完成"))
            assertFalse(page.view.allViews.any { it.contentDescription == "取消传分" })
        } finally {
            publish(base.copy(stage = CaptureStage.CANCELLED))
            page.dispose()
            host.removeView(page.view)
        }
    }
}
