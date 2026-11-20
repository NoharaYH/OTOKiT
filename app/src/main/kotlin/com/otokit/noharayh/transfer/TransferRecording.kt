package com.otokit.noharayh.transfer

import com.otokit.noharayh.score.HtmlBatchRef
import okhttp3.Interceptor
import java.io.Closeable

/** 传分提供事实；持久日志、HTTP 正文与封存由装配时注入的记录端处理。 */
internal class TransferRecording(
    private val append: (String, String, Map<String, String>) -> Unit,
    val interceptor: (String) -> Interceptor,
    private val bind: (HtmlBatchRef) -> Unit,
    private val complete: (String) -> Unit,
    val localHttp: (CaptureLocalHttpExchange) -> Unit,
    val retain: (() -> Closeable)? = null,
) {
    fun event(stage: String, message: String, data: Map<String, String> = emptyMap()) = isolate {
        append(stage, message, data)
    }
    fun bindBatch(batch: HtmlBatchRef) = isolate { bind(batch) }
    fun finish(status: String) = isolate { complete(status) }
    fun recordHttp(exchange: CaptureLocalHttpExchange) = isolate { localHttp(exchange) }

    // 记录是可失败的旁路，不能把磁盘异常传播成网络重试或改变任务结果。
    private inline fun isolate(action: () -> Unit) { try { action() } catch (_: Exception) { } }
}

internal data class CaptureLocalHttpExchange(
    val method: String,
    val url: String,
    val requestHeaders: List<Pair<String, String>>,
    val status: Int,
    val responseHeaders: List<Pair<String, String>>,
    val responseBody: ByteArray,
)
