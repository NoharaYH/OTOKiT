package com.otokit.noharayh.transfer.upload

import com.otokit.noharayh.auth.OAuthAuthorizationCheck
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.auth.ScoreGrantUnavailable
import com.otokit.noharayh.score.HtmlCaptures
import com.otokit.noharayh.score.HtmlPageRef
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.UploadOutcome
import com.otokit.noharayh.transfer.UploadReceipt
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

/** Only score HTML reaches these fixed first-party upload endpoints. Login/diagnostic pages stay local. */
internal class PlatformHtmlUpload(
    private val archives: HtmlCaptures,
    private val accessToken: (OAuthProvider, String, String) -> String,
    private val client: OkHttpClient = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS).build()
) {

    fun send(provider: OAuthProvider, subject: String, page: HtmlPageRef, onCall: (Call) -> Unit): UploadReceipt {
        val mai = page.batch.game == ScoreGame.MAIMAI
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
        val endpoint = when (provider) {
            OAuthProvider.DIVING_FISH -> "https://www.diving-fish.com/api/" +
                (if (mai) "maimaidxprober" else "chunithmprober") + "/player/update_records_html"
            OAuthProvider.LXNS -> "https://maimai.lxns.net/api/v0/user/${page.batch.game.key}/player/html"
        }
        return archives.open(page).use { html ->
            val body = object : RequestBody() {
                override fun contentType() = "text/plain; charset=utf-8".toMediaType()
                override fun contentLength() = page.bytes
                override fun isOneShot() = true
                override fun writeTo(sink: BufferedSink) { sink.writeAll(html.source()) }
            }
            val call = client.newCall(Request.Builder().url(endpoint).header("Authorization", "Bearer $token").post(body).build())
            onCall(call)
            try {
                call.execute().use { response ->
                    when {
                        response.code == 401 -> UploadReceipt(UploadOutcome.AUTH_REQUIRED, 401)
                        response.code == 403 -> UploadReceipt(UploadOutcome.PERMISSION_DENIED, 403)
                        response.code == 429 -> UploadReceipt(UploadOutcome.RATE_LIMITED, 429)
                        response.code >= 500 -> UploadReceipt(UploadOutcome.UNKNOWN, response.code)
                        !response.isSuccessful -> UploadReceipt(UploadOutcome.REJECTED, response.code)
                        else -> parseReceipt(provider, response.code, response.body?.string().orEmpty())
                    }
                }
            } catch (error: IOException) {
                UploadReceipt(UploadOutcome.UNKNOWN)
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
