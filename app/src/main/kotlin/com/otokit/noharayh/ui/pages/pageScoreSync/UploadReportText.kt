package com.otokit.noharayh.ui.pages.pageScoreSync

import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.transfer.*
import com.otokit.noharayh.ui.kit.pageScoreSync.ScoreSyncLogColors

private fun OAuthProvider.label() = if (this == OAuthProvider.DIVING_FISH) "水鱼" else "落雪"
private fun HtmlPageKind.label() = when (this) {
    HtmlPageKind.PLAYER -> "玩家资料"
    HtmlPageKind.RECENT -> "最近游玩"
    HtmlPageKind.UTAGE -> "U·TA·GE"
    HtmlPageKind.WORLDS_END -> "WORLD'S END"
    else -> suffix
}

internal fun formatUploadReport(report: UploadReport): String =
    report.log.joinToString("\n") { formatTransferEvent(it.event) }

private fun formatTransferEvent(event: TransferEvent): String = buildString {
    when (event) {
        is TransferEvent.Saved -> append("[DOWNLOAD][${event.completed}/${event.total}]已校验并保存 ${event.page.label()}")
        is TransferEvent.Upload -> appendReceipt(event.page)
        is TransferEvent.Stage -> {
            append("[SYSTEM]").append(when (event.stage) {
                CaptureStage.PREPARING -> "任务已受理，正在准备"
                CaptureStage.WAITING_WECHAT -> "等待游戏授权"
                CaptureStage.LOGIN -> "正在登录游戏"
                CaptureStage.FETCHING -> "正在采集游戏页面"
                CaptureStage.UPLOADING -> "页面已存档，开始上传"
                CaptureStage.COMPLETED -> "传分完成"
                CaptureStage.PARTIAL -> "部分完成，请查看各平台回执"
                CaptureStage.FAILED -> "任务失败"
                CaptureStage.CANCELLED -> "任务已取消"
                CaptureStage.TIMED_OUT -> "任务超时"
            })
            event.failure?.let { append(" · ").append(when (it) {
                CaptureFailure.NETWORK -> "网络请求失败"
                CaptureFailure.BUSINESS -> "游戏服务拒绝请求"
                CaptureFailure.INVALID_PAGE -> "页面校验失败"
                CaptureFailure.STORAGE -> "本地存档失败"
                CaptureFailure.PLATFORM -> "运行服务异常"
                CaptureFailure.INTERRUPTED -> "任务中断"
                CaptureFailure.UPLOAD -> "上传失败"
            }) }
            event.httpStatus?.let { append("[HTTP $it]") }
        }
    }
}

private fun StringBuilder.appendReceipt(page: UploadPageResult) {
    append("[UPLOAD]").append(page.provider.label())
    append('·').append(page.page.label()).append('·')
    append(when (page.receipt.outcome) {
        UploadOutcome.SENDING -> "正在提交"
        UploadOutcome.ACCEPTED, UploadOutcome.UPDATED, UploadOutcome.NO_CHANGES,
        UploadOutcome.NO_RECORDS -> "已接受"
        UploadOutcome.AUTH_REQUIRED -> "请确认原账号授权"
        UploadOutcome.AUTH_UNAVAILABLE -> "暂时无法取得上传授权"
        UploadOutcome.PERMISSION_DENIED -> "缺少写入权限"
        UploadOutcome.RATE_LIMITED -> "平台限流"
        UploadOutcome.UNKNOWN -> "提交结果待确认"
        UploadOutcome.REJECTED, UploadOutcome.PLAYER_NOT_FOUND -> "平台拒绝上传"
        UploadOutcome.UNSUPPORTED -> "此平台跳过该页面"
        UploadOutcome.BLOCKED -> "前置步骤未完成，已跳过"
    })
    page.receipt.httpStatus?.let { append('[').append(it).append(']') }
    if (page.receipt.outcome == UploadOutcome.PLAYER_NOT_FOUND) {
        append("\n[UPLOAD]您的落雪平台好像暂无分数，为保证落雪平台能正常上传成绩，请先使用落雪官网的代理上传上传一次。")
    }
    if (page.receipt.accepted) {
        append("\n[UPLOAD]").append(when {
            page.receipt.outcome in setOf(UploadOutcome.NO_CHANGES, UploadOutcome.NO_RECORDS) ||
                page.receipt.records == 0 -> "平台报告无新增成绩"
            page.receipt.records != null -> "平台新增成绩${page.receipt.records}条"
            else -> "平台未返回成绩数量"
        })
    }
}

internal fun styledUploadReport(report: UploadReport): CharSequence {
    val text = formatUploadReport(report)
    return SpannableString(text).apply {
        fun color(start: Int, end: Int, value: Int) =
            setSpan(ForegroundColorSpan(value), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        var start = 0
        for (line in report.log) {
            val event = line.event
            // 一条上传回执可占两行，按事件长度推进，避免后续事件的颜色错位。
            val end = start + formatTransferEvent(event).length
            val tint = when (event) {
                is TransferEvent.Saved -> ScoreSyncLogColors.SUCCESS
                is TransferEvent.Stage -> when (event.stage) {
                    CaptureStage.COMPLETED -> ScoreSyncLogColors.SUCCESS
                    CaptureStage.FAILED, CaptureStage.TIMED_OUT -> ScoreSyncLogColors.ERROR
                    CaptureStage.PARTIAL, CaptureStage.CANCELLED -> ScoreSyncLogColors.WARNING
                    else -> ScoreSyncLogColors.INFO
                }
                is TransferEvent.Upload -> when (event.page.receipt.outcome) {
                    UploadOutcome.SENDING -> ScoreSyncLogColors.INFO
                    UploadOutcome.ACCEPTED, UploadOutcome.UPDATED, UploadOutcome.NO_CHANGES,
                    UploadOutcome.NO_RECORDS -> ScoreSyncLogColors.SUCCESS
                    UploadOutcome.REJECTED, UploadOutcome.PLAYER_NOT_FOUND, UploadOutcome.AUTH_REQUIRED,
                    UploadOutcome.PERMISSION_DENIED -> ScoreSyncLogColors.ERROR
                    UploadOutcome.UNSUPPORTED -> ScoreSyncLogColors.MUTED
                    else -> ScoreSyncLogColors.WARNING
                }
            }
            val tagEnd = text.indexOf(']', start) + 1
            color(start, tagEnd, tint)
            setSpan(StyleSpan(Typeface.BOLD), start, tagEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (event is TransferEvent.Upload) {
                val platformStart = tagEnd
                color(platformStart, platformStart + event.page.provider.label().length, ScoreSyncLogColors.WARNING)
                val receiptStart = platformStart + event.page.provider.label().length + event.page.page.label().length + 2
                color(receiptStart, end, tint)
                if (event.page.receipt.accepted) {
                    val summaryStart = text.indexOf('\n', receiptStart) + 1
                    setSpan(StyleSpan(Typeface.BOLD), summaryStart, summaryStart + "[UPLOAD]".length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            } else if (event is TransferEvent.Stage && event.stage in setOf(CaptureStage.COMPLETED,
                    CaptureStage.PARTIAL, CaptureStage.FAILED, CaptureStage.CANCELLED, CaptureStage.TIMED_OUT)) {
                color(tagEnd, end, tint)
            }
            start = end + 1
        }
    }
}
