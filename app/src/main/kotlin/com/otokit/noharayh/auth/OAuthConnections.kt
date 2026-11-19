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
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

data class OAuthAccount(val name: String?, val grantedScopes: Set<String>, val refreshExpiresAt: Long?, val subject: String)

class ScoreGrantUnavailable(val reason: OAuthAuthorizationCheck) : Exception()

enum class OAuthFeedback {
    AUTHORIZED, CANCELLED, INVALID_CALLBACK, NETWORK_ERROR, REJECTED, LOCAL_ERROR,
}

enum class OAuthAuthorizationCheck {
    UNCHECKED, CHECKING, VALID, INVALID,
    RATE_LIMITED, APPLICATION_REJECTED, PERMISSION_DENIED, SERVICE_UNAVAILABLE, UNAVAILABLE,
}

/** Application-scoped owner for transactions and account summaries. Public callbacks run on the main thread. */
class OAuthConnections internal constructor(context: Context) {
    private val store = EncryptedOAuthStore(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val preparing = mutableSetOf<OAuthProvider>()
    private val accounts = mutableMapOf<OAuthProvider, OAuthAccount>()
    private val authorizationChecks = mutableMapOf<OAuthProvider, OAuthAuthorizationCheck>()
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

    /** Blocking worker API. Only the access token leaves auth, scoped to the account that started the task. */
    fun scoreAccessToken(provider: OAuthProvider, subject: String, scope: String): String {
        val result = worker.submit<String> {
            val key = accountKey(provider)
            val account = store.read(key)
            if (subject.isBlank() || account?.optString("sub") != subject) {
                throw ScoreGrantUnavailable(OAuthAuthorizationCheck.INVALID)
            }
            fun checkScope() {
                if (scope !in account.toAccount().grantedScopes) {
                    throw ScoreGrantUnavailable(OAuthAuthorizationCheck.PERMISSION_DENIED)
                }
            }
            checkScope()
            if (account.getLong("expires") <= System.currentTimeMillis() + 30_000L) {
                if ((account.refreshExpiry() ?: 0) <= System.currentTimeMillis()) {
                    throw ScoreGrantUnavailable(OAuthAuthorizationCheck.INVALID)
                }
                try {
                    refresh(provider, account)
                } catch (failure: OAuthRejected) {
                    main.post { reportAuthorizationCheck(provider, failure.checkResult) }
                    throw ScoreGrantUnavailable(failure.checkResult)
                }
                store.write(key, account)
                publish(provider, account.toAccount(), OAuthAuthorizationCheck.VALID)
                checkScope()
            }
            account.getString("accessToken")
        }
        return try {
            result.get()
        } catch (failure: ExecutionException) {
            throw (failure.cause ?: failure)
        }
    }

    fun authorizationCheck(provider: OAuthProvider): OAuthAuthorizationCheck =
        authorizationChecks[provider] ?: OAuthAuthorizationCheck.UNCHECKED

    fun checkAuthorization(provider: OAuthProvider) {
        if (authorizationCheck(provider) == OAuthAuthorizationCheck.CHECKING) return
        authorizationChecks[provider] = OAuthAuthorizationCheck.CHECKING
        notifyObservers()
        worker.execute {
            runSafely(provider, { reportAuthorizationCheck(provider, OAuthAuthorizationCheck.UNAVAILABLE) }) {
                val key = accountKey(provider)
                val account = store.read(key)
                if (provider == OAuthProvider.LXNS && account != null &&
                    account.optString("refreshToken").isNotBlank()
                ) {
                    val refreshed = try {
                        refresh(provider, account)
                    } catch (rejected: OAuthRejected) {
                        main.post { reportAuthorizationCheck(provider, rejected.checkResult) }
                        return@runSafely
                    }
                    store.write(key, refreshed)
                    publish(provider, refreshed.toAccount(), OAuthAuthorizationCheck.VALID)
                    return@runSafely
                }

                val expires = account?.refreshExpiry()
                val result = if (expires != null && expires > System.currentTimeMillis()) {
                    OAuthAuthorizationCheck.VALID
                } else {
                    OAuthAuthorizationCheck.INVALID
                }
                main.post { reportAuthorizationCheck(provider, result) }
            }
        }
    }

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
        val endpoint = ProviderOAuth.endpoint(provider.issuer, pending.getString("token"))
        val token = requestToken(endpoint, form)
        val accessToken = token.getString("access_token")
        val issuedAt = System.currentTimeMillis()
        val user = readJson(Request.Builder()
            .url(ProviderOAuth.endpoint(provider.issuer, pending.getString("userinfo")))
            .header("Authorization", "Bearer $accessToken")
            .build())
        return JSONObject()
            .put("sub", user.getString("sub"))
            .put("name", user.optString("preferred_username").ifBlank { user.optString("name") })
            .apply { updateTokens(token, endpoint, issuedAt) }
    }

    private fun refresh(provider: OAuthProvider, account: JSONObject): JSONObject {
        val endpoint = account.optString("tokenEndpoint").takeIf(String::isNotBlank)
            ?.let { ProviderOAuth.endpoint(provider.issuer, it) } ?: discover(provider).token
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", provider.clientId)
            .add("refresh_token", account.getString("refreshToken"))
            .build()
        val token = requestToken(endpoint, form)
        check(token.optString("refresh_token").isNotBlank())
        return account.apply { updateTokens(token, endpoint, System.currentTimeMillis()) }
    }

    private fun JSONObject.updateTokens(token: JSONObject, endpoint: HttpUrl, issuedAt: Long) {
        val accessToken = token.getString("access_token")
        val lifetime = token.getLong("expires_in")
        check(accessToken.isNotBlank() && lifetime > 0)
        put("accessToken", accessToken)
        put("refreshToken", token.optString("refresh_token"))
        put("expires", issuedAt + TimeUnit.SECONDS.toMillis(lifetime))
        put("refreshExpires", issuedAt + REFRESH_LIFETIME_MS)
        put("tokenEndpoint", endpoint.toString())
        if (token.has("scope")) put("scope", token.getString("scope"))
    }

    private fun requestToken(endpoint: HttpUrl, form: FormBody): JSONObject {
        val oneShot = object : RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
            override fun isOneShot() = true
        }
        return readJson(Request.Builder().url(endpoint).post(oneShot).build(), tokenRequest = true)
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

    private fun readJson(request: Request, tokenRequest: Boolean = false): JSONObject = client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            val error = if (tokenRequest && response.code in 400..499) {
                readOAuthError(response.body?.string().orEmpty())
            } else null
            throw OAuthRejected(response.code, ProviderOAuth.checkFailure(response.code, error, tokenRequest))
        }
        JSONObject(requireNotNull(response.body).string())
    }

    private fun readOAuthError(body: String): String? = try {
        JSONObject(body).optString("error").takeIf(String::isNotBlank)
    } catch (_: JSONException) {
        null
    }

    private fun publish(provider: OAuthProvider, account: OAuthAccount, check: OAuthAuthorizationCheck? = null) {
        main.post {
            accounts[provider] = account
            // A queued check reads the new authorization on the same worker.
            if (check != null) authorizationChecks[provider] = check
            else if (authorizationCheck(provider) != OAuthAuthorizationCheck.CHECKING) authorizationChecks.remove(provider)
            notifyObservers()
        }
    }

    private fun reportAuthorizationCheck(provider: OAuthProvider, result: OAuthAuthorizationCheck) {
        authorizationChecks[provider] = result
        notifyObservers()
    }

    private fun notifyObservers() = listeners.toList().forEach { it() }

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
        refreshExpiresAt = refreshExpiry(),
        subject = optString("sub"),
    )

    private fun JSONObject.refreshExpiry(): Long? {
        if (optString("refreshToken").isBlank()) return null
        if (has("refreshExpires")) return getLong("refreshExpires")
        // Earlier app versions stored only the 15-minute authorization-code access expiry.
        return getLong("expires") - LEGACY_ACCESS_LIFETIME_MS + REFRESH_LIFETIME_MS
    }

    private fun pendingKey(provider: OAuthProvider) = "pending_${provider.name}"
    private fun accountKey(provider: OAuthProvider) = "account_${provider.name}"
    private data class Endpoints(val authorization: HttpUrl, val token: HttpUrl, val userInfo: HttpUrl)
    private class OAuthRejected(val status: Int, val checkResult: OAuthAuthorizationCheck) : IOException()

    private companion object {
        val REFRESH_LIFETIME_MS = TimeUnit.DAYS.toMillis(30)
        val LEGACY_ACCESS_LIFETIME_MS = TimeUnit.MINUTES.toMillis(15)
    }
}
