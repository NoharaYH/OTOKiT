package com.otokit.noharayh.ui.pages.pageScoreSync

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.transfer.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun formatUploadReport(report: UploadReport): String = buildString {
    append("[SYSTEM] 传分开始 ").append(time.format(report.startedAt.atZone(ZoneId.systemDefault())))
    for (page in report.pages) {
        append("\n[UPLOAD] [").append(if (page.provider == OAuthProvider.DIVING_FISH) "水鱼" else "落雪")
        append("] ").append(page.page.suffix).append(" · ")
        append(when (page.receipt.outcome) {
            UploadOutcome.SENDING -> "正在提交"
            UploadOutcome.ACCEPTED -> "平台已接受"
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
    val end = when (report.stage) {
        CaptureStage.COMPLETED -> "传分完成"
        CaptureStage.PARTIAL -> "部分完成"
        CaptureStage.FAILED -> "任务失败"
        CaptureStage.CANCELLED -> "任务已取消"
        CaptureStage.TIMED_OUT -> "任务超时"
        else -> null
    }
    if (end != null) append("\n[SYSTEM] ").append(end)
}
