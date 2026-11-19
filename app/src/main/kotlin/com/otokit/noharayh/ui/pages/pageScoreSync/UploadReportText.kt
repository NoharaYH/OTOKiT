package com.otokit.noharayh.ui.pages.pageScoreSync

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.transfer.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val clock = DateTimeFormatter.ofPattern("HH:mm:ss")

private fun OAuthProvider.label() = if (this == OAuthProvider.DIVING_FISH) "水鱼" else "落雪"
private fun HtmlPageKind.label() = when (this) {
    HtmlPageKind.PLAYER -> "玩家资料"
    HtmlPageKind.RECENT -> "最近游玩"
    HtmlPageKind.UTAGE -> "U·TA·GE"
    HtmlPageKind.WORLDS_END -> "WORLD'S END"
    else -> suffix
}

internal fun formatUploadReport(report: UploadReport): String = buildString {
    val zone = ZoneId.systemDefault()
    append(time.format(report.startedAt.atZone(zone))).append(" · ").append(report.targets.joinToString("、") { it.label() })
    append(" · ").append(report.difficulties.joinToString("、") { it.label() })
    for (line in report.log) {
        append('\n').append(clock.format(line.at.atZone(zone))).append(' ')
        when (val event = line.event) {
            is TransferEvent.Saved -> append("[DOWNLOAD] 已校验并保存 ${event.page.label()}（${event.completed}/${event.total}）")
            is TransferEvent.Upload -> appendReceipt(event.page)
            is TransferEvent.Stage -> {
                append("[SYSTEM] ").append(when (event.stage) {
                    CaptureStage.PREPARING -> "任务已受理，正在准备"
                    CaptureStage.WAITING_WECHAT -> "等待游戏授权，请在微信文件传输助手中粘贴、发送并打开入口链接"
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
                event.httpStatus?.let { append(" [HTTP $it]") }
            }
        }
    }
}

private fun StringBuilder.appendReceipt(page: UploadPageResult) {
    append("[UPLOAD] [").append(page.provider.label())
    append("] ").append(page.page.label()).append(" · ")
    append(when (page.receipt.outcome) {
        UploadOutcome.SENDING -> "正在提交"
        UploadOutcome.ACCEPTED -> page.receipt.records?.let { "平台已接受，写入 ${it} 条" } ?: "平台已接受"
        UploadOutcome.UPDATED -> "更新 ${page.receipt.records} 条"
        UploadOutcome.NO_CHANGES -> "已接受，无成绩变化"
        UploadOutcome.NO_RECORDS -> "已接受，平台报告写入 0 条"
        UploadOutcome.AUTH_REQUIRED -> "请确认原账号授权"
        UploadOutcome.AUTH_UNAVAILABLE -> "暂时无法取得上传授权"
        UploadOutcome.PERMISSION_DENIED -> "缺少写入权限"
        UploadOutcome.RATE_LIMITED -> "平台限流"
        UploadOutcome.UNKNOWN -> "提交结果待确认"
        UploadOutcome.REJECTED -> "平台拒绝上传"
        UploadOutcome.UNSUPPORTED -> "此平台跳过该页面"
        UploadOutcome.BLOCKED -> "前置步骤未完成，已跳过"
    })
    page.receipt.httpStatus?.let { append(" [").append(it).append(']') }
}
