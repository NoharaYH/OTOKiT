package com.otokit.noharayh.auth

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.otokit.noharayh.network.NetworkRoute
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.ProxyScope
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Interceptor
import okio.BufferedSink
import org.json.JSONException
import org.json.JSONObject
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

data class OAuthAccount(val name: String?, val grantedScopes: Set<String>, val refreshExpiresAt: Long?, val subject: String)

class ScoreGrantUnavailable(val reason: OAuthAuthorizationCheck) : Exception()

/** 仅本次传分取票的观察入口；设置页授权和检查不继承此上下文。 */
internal class ScoreTokenObserver(val interceptor: Interceptor, val event: (String) -> Unit,
                                 val network: (String, Map<String, String>) -> Unit = { _, _ -> })

enum class OAuthFeedback {
    AUTHORIZED, CANCELLED, INVALID_CALLBACK, NETWORK_ERROR, REJECTED, LOCAL_ERROR,
}

enum class OAuthAuthorizationCheck {
    UNCHECKED, CHECKING, VALID, INVALID,
    RATE_LIMITED, APPLICATION_REJECTED, PERMISSION_DENIED, SERVICE_UNAVAILABLE, UNAVAILABLE,
}

/** Application-scoped owner for transactions and account summaries. Public callbacks run on the main thread. */
class OAuthConnections internal constructor(
    context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(12, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false)
        .retryOnConnectionFailure(false).build(),
    private val snapshot: () -> NetworkSnapshot = { NetworkSnapshot.defaults() },
) {
    private val store = EncryptedOAuthStore(context.applicationContext)
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val listeners = mutableSetOf<() -> Unit>()
    private val preparing = mutableSetOf<OAuthProvider>()
    private val accounts = mutableMapOf<OAuthProvider, OAuthAccount>()
    private val authorizationChecks = mutableMapOf<OAuthProvider, OAuthAuthorizationCheck>()
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
        return scoreAccessToken(provider, subject, scope, null)
    }

    internal fun scoreAccessToken(provider: OAuthProvider, subject: String, scope: String,
                                  observer: ScoreTokenObserver?, retain: (() -> Closeable)? = null,
                                  network: NetworkSnapshot = snapshot()): String {
        // caller 被中断后，已经排队的取票仍由 auth worker 完成，记录端也必须等到它真正退出。
        val retained = retain?.invoke()
        val result = try { worker.submit<String> {
            try {
            observer?.event?.invoke("检查 ${provider.name} 上传授权及所需 scope")
            val key = accountKey(provider)
            val account = store.read(key)
            if (subject.isBlank() || account?.optString("sub") != subject) {
                throw ScoreGrantUnavailable(OAuthAuthorizationCheck.INVALID)
            }
            if (account.issuer(provider) != provider.issuer(network)) {
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
                    observer?.event?.invoke("刷新 ${provider.name} 上传票据")
                    refresh(provider, account, network, observer)
                } catch (failure: OAuthRejected) {
                    main.post { reportAuthorizationCheck(provider, failure.checkResult) }
                    throw ScoreGrantUnavailable(failure.checkResult)
                }
                store.write(key, account)
                publish(provider, account.toAccount(), OAuthAuthorizationCheck.VALID)
                checkScope()
                observer?.event?.invoke("${provider.name} 上传票据已刷新并保存")
            } else observer?.event?.invoke("复用 ${provider.name} 有效上传票据")
            account.getString("accessToken")
            } finally { retained?.close() }
        } } catch (failure: RejectedExecutionException) {
            retained?.close()
            throw failure
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
        val network = snapshot()
        authorizationChecks[provider] = OAuthAuthorizationCheck.CHECKING
        notifyObservers()
        worker.execute {
            runSafely(provider, { reportAuthorizationCheck(provider, OAuthAuthorizationCheck.UNAVAILABLE) }) {
                val key = accountKey(provider)
                val account = store.read(key)
                if (account != null && account.issuer(provider) != provider.issuer(network)) {
                    main.post { reportAuthorizationCheck(provider, OAuthAuthorizationCheck.INVALID) }
                    return@runSafely
                }
                if (provider == OAuthProvider.LXNS && account != null &&
                    account.optString("refreshToken").isNotBlank()
                ) {
                    val refreshed = try {
                        refresh(provider, account, network)
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
        val network = snapshot()
        worker.execute {
            try {
                runSafely(provider, report) {
                    val issuer = provider.issuer(network)
                    val document = discover(provider, network)
                    val state = ProviderOAuth.nonce()
                    val verifier = ProviderOAuth.nonce()
                    store.write(pendingKey(provider), JSONObject()
                        .put("state", state)
                        .put("verifier", verifier)
                        .put("redirect", provider.redirectUri)
                        .put("issuer", issuer)
                        .put("network", network.toJson())
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
        // Old transactions predate configurable routes and always used the built-in endpoints.
        val network = pending.optJSONObject("network")?.let { NetworkSnapshot.fromJson(it) } ?: NetworkSnapshot.defaults()
        val issuer = pending.optString("issuer").ifBlank { provider.issuer }
        val form = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", provider.clientId)
            .add("redirect_uri", pending.getString("redirect"))
            .add("code", code)
            .add("code_verifier", pending.getString("verifier"))
            .build()
        val endpoint = ProviderOAuth.endpoint(issuer, pending.getString("token"))
        val token = requestToken(endpoint, form, network)
        val accessToken = token.getString("access_token")
        val issuedAt = System.currentTimeMillis()
        val user = readJson(Request.Builder()
            .url(ProviderOAuth.endpoint(issuer, pending.getString("userinfo")))
            .header("Authorization", "Bearer $accessToken")
            .build(), network = network)
        return JSONObject()
            .put("issuer", issuer)
            .put("sub", user.getString("sub"))
            .put("name", user.optString("preferred_username").ifBlank { user.optString("name") })
            .apply { updateTokens(token, endpoint, issuedAt) }
    }

    private fun refresh(provider: OAuthProvider, account: JSONObject, network: NetworkSnapshot,
                        observer: ScoreTokenObserver? = null): JSONObject {
        val issuer = account.issuer(provider)
        observer?.network?.invoke("使用任务固定的授权服务配置", mapOf(
            "configurationRevision" to network.revision.toString(), "originalTarget" to provider.issuer,
            "effectiveTarget" to issuer))
        val endpoint = account.optString("tokenEndpoint").takeIf(String::isNotBlank)
            ?.let { ProviderOAuth.endpoint(issuer, it) } ?: discover(provider, network, observer).token
        val form = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", provider.clientId)
            .add("refresh_token", account.getString("refreshToken"))
            .build()
        val token = requestToken(endpoint, form, network, observer)
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

    private fun requestToken(endpoint: HttpUrl, form: FormBody, network: NetworkSnapshot,
                             observer: ScoreTokenObserver? = null): JSONObject {
        val oneShot = object : RequestBody() {
            override fun contentType() = form.contentType()
            override fun contentLength() = form.contentLength()
            override fun writeTo(sink: BufferedSink) = form.writeTo(sink)
            override fun isOneShot() = true
        }
        return readJson(Request.Builder().url(endpoint).post(oneShot).build(), tokenRequest = true,
            observer = observer, network = network)
    }

    private fun discover(provider: OAuthProvider, network: NetworkSnapshot, observer: ScoreTokenObserver? = null): Endpoints {
        val issuer = provider.issuer(network)
        val url = (issuer + "/.well-known/openid-configuration").toHttpUrl()
        val json = readJson(Request.Builder().url(url).build(), observer = observer, network = network)
        require(json.getString("issuer") == issuer)
        return Endpoints(
            ProviderOAuth.endpoint(issuer, json.getString("authorization_endpoint")),
            ProviderOAuth.endpoint(issuer, json.getString("token_endpoint")),
            ProviderOAuth.endpoint(issuer, json.getString("userinfo_endpoint")),
        )
    }

    private fun readJson(request: Request, tokenRequest: Boolean = false, observer: ScoreTokenObserver? = null,
                         network: NetworkSnapshot): JSONObject {
        val routed = NetworkRoute.client(client, network, ProxyScope.PLATFORM)
        val transport = observer?.let { routed.newBuilder().addNetworkInterceptor(it.interceptor).build() } ?: routed
        val started = System.nanoTime()
        var failure = ""
        return try {
            transport.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val error = if (tokenRequest && response.code in 400..499) {
                        readOAuthError(response.body?.string().orEmpty())
                    } else null
                    throw OAuthRejected(response.code, ProviderOAuth.checkFailure(response.code, error, tokenRequest))
                }
                JSONObject(requireNotNull(response.body).string())
            }
        } catch (error: IOException) {
            failure = if (error is OAuthRejected) "HTTP_${error.status}" else "NETWORK"
            throw error
        } catch (error: JSONException) {
            failure = "RESPONSE"
            throw error
        } finally {
            observer?.network?.invoke("平台授权请求结束", mapOf(
                "configurationRevision" to network.revision.toString(),
                "effectiveTarget" to request.url.newBuilder().query(null).build().toString(),
                "route" to if (ProxyScope.PLATFORM in network.scopes) "UPSTREAM" else "DIRECT",
                "durationMs" to TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started).toString(),
                "failureStage" to failure))
        }
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

    private fun JSONObject.issuer(provider: OAuthProvider): String = optString("issuer").ifBlank { provider.issuer }

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
