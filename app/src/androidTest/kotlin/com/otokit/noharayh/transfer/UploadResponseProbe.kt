package com.otokit.noharayh.transfer

import android.os.SystemClock
import android.os.Bundle
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.composition.oauthConnections
import com.otokit.noharayh.score.*
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import okhttp3.OkHttpClient
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/** Opt-in: one explicitly selected archived page and provider per invocation. */
@RunWith(AndroidJUnit4::class)
class UploadResponseProbe {
    @Test fun observeSingleReply() {
        val args = InstrumentationRegistry.getArguments()
        assumeTrue(args.getString("liveUpload") == "yes")
        val game = ScoreGame.valueOf(requireNotNull(args.getString("game")))
        val kind = HtmlPageKind.valueOf(requireNotNull(args.getString("difficulty")))
        val provider = OAuthProvider.valueOf(requireNotNull(args.getString("provider")))
        require(kind.isDifficulty)
        val inst = InstrumentationRegistry.getInstrumentation()
        val ctx = inst.targetContext
        val archive = FileHtmlCaptures(File(ctx.filesDir, "score/html"))
        val ref = archive.list(game).single { it.id == args.getString("batch") }
        val batch = archive.read(ref)
        assertEquals(HtmlBatchStatus.COMPLETED, batch.status)
        assertEquals(HtmlValidity.VALID, batch.evidence[kind]?.validity)
        val page = batch.pages.single { it.kind == kind }
        val auth = oauthConnections(ctx)
        var subject: String? = null
        val until = SystemClock.elapsedRealtime() + 3000
        do {
            inst.runOnMainSync { subject = auth.account(provider)?.subject }
            if (subject != null) break
            SystemClock.sleep(50)
        } while (SystemClock.elapsedRealtime() < until)
        assertNotNull("No authorized account for $provider", subject)
        val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .retryOnConnectionFailure(false).callTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                val peek = response.peekBody(65536).string()
                val obj = runCatching { JSONObject(peek) }.getOrNull()
                val fields = obj?.keys()?.asSequence()?.toList()?.sorted().orEmpty()
                val values = listOf("success", "code", "updates", "creates").mapNotNull { key ->
                    val v = obj?.opt(key)
                    if (v is Boolean || v is Number) "$key=$v" else null
                }
                val data = obj?.optJSONArray("data")
                val dataFields = data?.optJSONObject(0)?.keys()?.asSequence()?.toList()?.sorted().orEmpty()
                val summary = JSONObject()
                for (key in listOf("success", "code", "updates", "creates", "status", "message")) {
                    if (obj != null && obj.has(key)) summary.put(key, obj.get(key))
                }
                if (data != null && data.length() == 0) summary.put("data", data)
                inst.sendStatus(2, Bundle().apply {
                    putString("reply", "$game/$kind/$provider HTTP=${response.code} $summary")
                    if (data != null && data.length() > 0) putString("changes", "count=${data.length()} fields=$dataFields")
                })
                Log.i("ScoreUploadTest", "live $game/$kind/$provider HTTP=${response.code} fields=$fields values=$values dataCount=${data?.length()} dataFields=$dataFields")
                response
            }.build()
        val start = SystemClock.elapsedRealtime()
        val receipt = PlatformHtmlUpload(archive, auth::scoreAccessToken, client).send(provider, requireNotNull(subject), page) {}
        Log.i("ScoreUploadTest", "live $game/$kind/$provider elapsed=${SystemClock.elapsedRealtime() - start}ms receipt=$receipt")
        assertTrue("Upload result: $receipt", receipt.accepted)
    }
}
