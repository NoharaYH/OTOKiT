package com.otokit.noharayh.transfer.upload

import com.otokit.noharayh.auth.OAuthAuthorizationCheck
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.auth.ScoreGrantUnavailable
import com.otokit.noharayh.network.NetworkRoute
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.score.HtmlCaptures
import com.otokit.noharayh.score.HtmlPageRef
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.UploadOutcome
import com.otokit.noharayh.transfer.UploadReceipt
import com.otokit.noharayh.transfer.TransferRecording
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Only score HTML reaches the task's confirmed upload targets. Login/diagnostic pages stay local. */
internal class PlatformHtmlUpload(
    private val archives: HtmlCaptures,
    private val accessToken: (OAuthProvider, String, String) -> String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build(),
    private val recording: TransferRecording? = null,
    private val network: NetworkSnapshot = NetworkSnapshot.defaults(),
) {
    private val routed = NetworkRoute.client(client, network, ProxyScope.PLATFORM)
    private val transport = recording?.let { routed.newBuilder().addNetworkInterceptor(it.interceptor("UPLOAD")).build() } ?: routed

    fun send(provider: OAuthProvider, subject: String, page: HtmlPageRef, onCall: (Call) -> Unit): UploadReceipt {
        recording?.event("UPLOAD", "准备上传 HTML", mapOf("provider" to provider.name, "page" to page.kind.name,
            "file" to page.fileName, "bytes" to page.bytes.toString()))
        val mai = page.batch.game == ScoreGame.MAIMAI
        UploadRequestTargets.validateRoute(network, provider, mai)
        val endpoint = UploadRequestTargets.url(network, provider, mai)
        val token = try {
            accessToken(provider, subject, provider.scoreWriteScope(mai))
        } catch (error: ScoreGrantUnavailable) {
            return UploadReceipt(when (error.reason) {
                OAuthAuthorizationCheck.PERMISSION_DENIED -> UploadOutcome.PERMISSION_DENIED
                OAuthAuthorizationCheck.INVALID -> UploadOutcome.AUTH_REQUIRED
                else -> UploadOutcome.AUTH_UNAVAILABLE
            })
        } catch (error: IOException) {
            return UploadReceipt(UploadOutcome.AUTH_UNAVAILABLE)
        }
        val target = UploadRequestTargets.key(provider, mai)
        recording?.event("UPLOAD", "使用任务固定的上传配置", mapOf(
            "configurationRevision" to network.revision.toString(),
            "originalTarget" to requireNotNull(UploadRequestTargets.defaultUrl(target)),
            "effectiveTarget" to endpoint.toString(),
            "route" to if (ProxyScope.PLATFORM in network.scopes) "UPSTREAM" else "DIRECT"))
        return archives.open(page).use { html ->
            val body = object : RequestBody() {
                override fun contentType() = "text/plain; charset=utf-8".toMediaType()
                override fun contentLength() = page.bytes
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.writeAll(html.source()) }
            }
            val call = transport.newCall(Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(body).build())
            onCall(call)
            val started = System.nanoTime()
            var failure = ""
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) failure = "HTTP_${response.code}"
                    when {
                        response.code == 401 -> UploadReceipt(UploadOutcome.AUTH_REQUIRED, 401)
                        response.code == 403 -> UploadReceipt(UploadOutcome.PERMISSION_DENIED, 403)
                        response.code == 429 -> UploadReceipt(UploadOutcome.RATE_LIMITED, 429)
                        response.code >= 500 -> UploadReceipt(UploadOutcome.UNKNOWN, response.code)
                        response.code == 404 && provider == OAuthProvider.LXNS -> {
                            val missing = try {
                                JSONObject(response.body?.string().orEmpty()).optString("message").trim()
                                    .equals("player not found", ignoreCase = true)
                            } catch (_: JSONException) { false }
                            catch (_: IOException) { false }
                            UploadReceipt(if (missing) UploadOutcome.PLAYER_NOT_FOUND else UploadOutcome.REJECTED, 404)
                        }
                        !response.isSuccessful -> UploadReceipt(UploadOutcome.REJECTED, response.code)
                        else -> parseReceipt(provider, response.code, response.body?.string().orEmpty())
                    }
                }
            } catch (error: IOException) {
                failure = "NETWORK"
                UploadReceipt(UploadOutcome.UNKNOWN)
            } finally {
                recording?.event("UPLOAD", "平台上传请求结束", mapOf(
                    "configurationRevision" to network.revision.toString(),
                    "durationMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).toString(),
                    "failureStage" to failure))
            }
        }
    }

    internal fun parseReceipt(provider: OAuthProvider, status: Int, body: String): UploadReceipt {
        try {
            val json = JSONObject(body)
            if (provider == OAuthProvider.LXNS) {
                if (!json.getBoolean("success") || json.getInt("code") != 200) return UploadReceipt(UploadOutcome.REJECTED, status)
                val changes = json.optJSONArray("data")?.length() ?: return UploadReceipt(UploadOutcome.ACCEPTED, status)
                return UploadReceipt(if (changes == 0) UploadOutcome.NO_CHANGES else UploadOutcome.UPDATED, status, changes)
            }
            if (json.optString("status") == "error") return UploadReceipt(UploadOutcome.REJECTED, status)
            val count = json.getInt("updates") + json.getInt("creates")
            // 水鱼 updates 包含已有谱面的写入，不能把它解释成分数提高的数量。
            return UploadReceipt(if (count == 0) UploadOutcome.NO_RECORDS else UploadOutcome.ACCEPTED, status, count)
        } catch (error: JSONException) {
            return UploadReceipt(UploadOutcome.UNKNOWN, status)
        }
    }
}
