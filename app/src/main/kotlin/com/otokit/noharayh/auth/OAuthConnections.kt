package com.otokit.noharayh.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class OAuthAccount(val name: String?, val grantedScopes: Set<String>, val expiresAt: Long) {
    val expired: Boolean get() = System.currentTimeMillis() >= expiresAt
}

enum class OAuthFeedback {
    AUTHORIZED, CANCELLED, INVALID_CALLBACK, NETWORK_ERROR, REJECTED, LOCAL_ERROR,
}

/** Application-scoped owner for transactions and account summaries. Public callbacks run on the main thread. */
class OAuthConnections internal constructor(context: Context) {
    private val store = EncryptedOAuthStore(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val preparing = mutableSetOf<OAuthProvider>()
    private val accounts = mutableMapOf<OAuthProvider, OAuthAccount>()
    private val client = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    init {
        worker.execute {
            for (provider in OAuthProvider.entries) {
                runSafely(provider, {}) {
                    store.read(accountKey(provider))?.let { publish(provider, it.toAccount()) }
                }
            }
        }
    }

    fun account(provider: OAuthProvider): OAuthAccount? = accounts[provider]

    fun observe(listener: () -> Unit): Closeable {
        listeners.add(listener)
        listener()
        return Closeable { listeners.remove(listener) }
    }

    fun authorize(provider: OAuthProvider, openBrowser: (String) -> Unit, report: (OAuthFeedback) -> Unit) {
        if (!preparing.add(provider)) return
        worker.execute {
            try {
                runSafely(provider, report) {
                    val document = discover(provider)
                    val state = ProviderOAuth.nonce()
                    val verifier = ProviderOAuth.nonce()
                    store.write(pendingKey(provider), JSONObject()
                        .put("state", state)
                        .put("verifier", verifier)
                        .put("redirect", provider.redirectUri)
                        .put("expires", System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10))
                        .put("token", document.token.toString())
                        .put("userinfo", document.userInfo.toString()))
                    val url = document.authorization.newBuilder()
                        .addQueryParameter("response_type", "code")
                        .addQueryParameter("client_id", provider.clientId)
                        .addQueryParameter("redirect_uri", provider.redirectUri)
                        .addQueryParameter("scope", provider.scopes)
                        .addQueryParameter("state", state)
                        .addQueryParameter("code_challenge", ProviderOAuth.challenge(verifier))
                        .addQueryParameter("code_challenge_method", "S256")
                        .build()
                    main.post { openBrowser(url.toString()) }
                }
            } finally {
                main.post { preparing.remove(provider) }
            }
        }
    }

    fun receive(value: String, report: (OAuthProvider, OAuthFeedback) -> Unit): Boolean {
        val provider = ProviderOAuth.callbackProvider(value) ?: return false
        val feedback = { result: OAuthFeedback -> report(provider, result) }
        worker.execute {
            runSafely(provider, feedback) {
                val key = pendingKey(provider)
                val pending = store.read(key)
                val result = pending?.let {
                    ProviderOAuth.callbackResult(value, it.getString("state"))
                }
                if (pending == null || result == null || pending.getString("redirect") != provider.redirectUri ||
                    System.currentTimeMillis() >= pending.getLong("expires")
                ) {
                    main.post { feedback(OAuthFeedback.INVALID_CALLBACK) }
                    return@runSafely
                }
                // Consume before sending the request. The authorization code must never be retried.
                store.remove(key)
                if (result is ProviderOAuth.CallbackResult.Error) {
                    main.post {
                        feedback(if (result.value == "access_denied") OAuthFeedback.CANCELLED else OAuthFeedback.REJECTED)
                    }
                    return@runSafely
                }
                val code = (result as ProviderOAuth.CallbackResult.Code).value
                val account = exchange(provider, pending, code)
                store.write(accountKey(provider), account)
                publish(provider, account.toAccount())
                main.post { feedback(OAuthFeedback.AUTHORIZED) }
            }
        }
        return true
    }

    private fun exchange(provider: OAuthProvider, pending: JSONObject, code: String): JSONObject {
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", provider.clientId)
            .add("redirect_uri", pending.getString("redirect"))
            .add("code", code)
            .add("code_verifier", pending.getString("verifier"))
            .build()
        val oneShot = object : RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
            override fun isOneShot() = true
        }
        val token = readJson(Request.Builder()
            .url(ProviderOAuth.endpoint(provider.issuer, pending.getString("token")))
            .post(oneShot)
            .build())
        val accessToken = token.getString("access_token")
        val expires = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(token.getLong("expires_in"))
        val user = readJson(Request.Builder()
            .url(ProviderOAuth.endpoint(provider.issuer, pending.getString("userinfo")))
            .header("Authorization", "Bearer $accessToken")
            .build())
        return JSONObject()
            .put("sub", user.getString("sub"))
            .put("name", user.optString("preferred_username").ifBlank { user.optString("name") })
            .put("accessToken", accessToken)
            .put("refreshToken", token.optString("refresh_token"))
            .put("scope", token.optString("scope"))
            .put("expires", expires)
    }

    private fun discover(provider: OAuthProvider): Endpoints {
        val url = (provider.issuer + "/.well-known/openid-configuration").toHttpUrl()
        val json = readJson(Request.Builder().url(url).build())
        require(json.getString("issuer") == provider.issuer)
        return Endpoints(
            ProviderOAuth.endpoint(provider.issuer, json.getString("authorization_endpoint")),
            ProviderOAuth.endpoint(provider.issuer, json.getString("token_endpoint")),
            ProviderOAuth.endpoint(provider.issuer, json.getString("userinfo_endpoint")),
        )
    }

    private fun readJson(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) throw OAuthRejected(response.code)
        JSONObject(requireNotNull(response.body).string())
    }

    private fun publish(provider: OAuthProvider, account: OAuthAccount) {
        main.post {
            accounts[provider] = account
            listeners.toList().forEach { it() }
        }
    }

    private fun runSafely(provider: OAuthProvider, report: (OAuthFeedback) -> Unit, action: () -> Unit) {
        try {
            action()
        } catch (failure: Exception) {
            val result = when (failure) {
                is OAuthRejected -> OAuthFeedback.REJECTED
                is IOException -> OAuthFeedback.NETWORK_ERROR
                else -> OAuthFeedback.LOCAL_ERROR
            }
            // Never log request URIs, exception messages, authorization codes or credentials.
            Log.w("ProviderOAuth", "${provider.name}: ${result.name} ${(failure as? OAuthRejected)?.status ?: ""}")
            main.post { report(result) }
        }
    }

    private fun JSONObject.toAccount() = OAuthAccount(
        name = optString("name").takeIf(String::isNotBlank),
        grantedScopes = optString("scope").split(' ').filter(String::isNotBlank).toSet(),
        expiresAt = getLong("expires"),
    )

    private fun pendingKey(provider: OAuthProvider) = "pending_${provider.name}"
    private fun accountKey(provider: OAuthProvider) = "account_${provider.name}"
    private data class Endpoints(val authorization: HttpUrl, val token: HttpUrl, val userInfo: HttpUrl)
    private class OAuthRejected(val status: Int) : IOException()
}
