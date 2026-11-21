package com.otokit.noharayh.auth

import com.otokit.noharayh.BuildConfig
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

enum class OAuthProvider(
    internal val issuer: String,
    internal val clientId: String,
    internal val callbackPath: String,
    private val maimaiWrite: String,
    private val chunithmWrite: String = maimaiWrite,
) {
    DIVING_FISH(
        "https://auth.diving-fish.com",
        BuildConfig.DIVING_FISH_CLIENT_ID,
        BuildConfig.DIVING_FISH_CALLBACK_PATH,
        "prober.records.write", "chunithm.records.write",
    ),
    LXNS(
        "https://maimai.lxns.net",
        BuildConfig.LXNS_CLIENT_ID,
        BuildConfig.LXNS_CALLBACK_PATH,
        "write_player",
    );

    fun scoreWriteScope(mai: Boolean): String = if (mai) maimaiWrite else chunithmWrite
    val defaultIssuer: String get() = issuer
    val issuerTarget: RequestTarget get() = when (this) {
        DIVING_FISH -> RequestTarget.OAUTH_FISH_ISSUER
        LXNS -> RequestTarget.OAUTH_LXNS_ISSUER
    }

    /** OAuth issuer 变更必须通过重新授权取得该来源的凭据。 */
    fun validateIssuer(value: String): String {
        val url = value.trim().toHttpUrlOrNull()
        require(url != null && url.isHttps && url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty() &&
            url.query == null && url.fragment == null) { "授权服务地址须为 HTTPS，且不能包含账号、密码、查询参数或片段。" }
        return url.toString().removeSuffix("/")
    }

    internal fun issuer(snapshot: NetworkSnapshot): String = validateIssuer(snapshot.target(issuerTarget, issuer))
    internal val writes: List<String> get() = listOf(maimaiWrite, chunithmWrite).distinct()
    internal val redirectUri: String get() = "https://${BuildConfig.OAUTH_CALLBACK_HOST}$callbackPath"
    internal val scopes: String get() {
        val requestedWrites = if (this == DIVING_FISH && !BuildConfig.DIVING_FISH_REQUEST_WRITE) emptyList() else writes
        return (listOf("openid", "profile") + requestedWrites).joinToString(" ")
    }
}

internal object ProviderOAuth {
    private val random = SecureRandom()
    private val base64 = Base64.getUrlEncoder().withoutPadding()

    fun nonce(): String = base64.encodeToString(ByteArray(32).also(random::nextBytes))

    fun challenge(verifier: String): String =
        base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    fun endpoint(issuer: String, value: String): HttpUrl {
        val base = issuer.toHttpUrl()
        val url = value.toHttpUrl()
        require(url.scheme == base.scheme && url.host == base.host && url.port == base.port &&
            url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty() && url.fragment == null)
        return url
    }

    fun callbackProvider(value: String): OAuthProvider? {
        val url = value.toHttpUrlOrNull() ?: return null
        if (!url.isHttps || url.port != 443 || url.host != BuildConfig.OAUTH_CALLBACK_HOST ||
            url.encodedUsername.isNotEmpty() || url.encodedPassword.isNotEmpty() || url.fragment != null
        ) return null
        return OAuthProvider.entries.firstOrNull { it.callbackPath == url.encodedPath }
    }

    fun checkFailure(status: Int, error: String?, tokenRequest: Boolean): OAuthAuthorizationCheck = when {
        status == 429 || error == "slow_down" -> OAuthAuthorizationCheck.RATE_LIMITED
        status in 500..599 || error == "server_error" || error == "temporarily_unavailable" ->
            OAuthAuthorizationCheck.SERVICE_UNAVAILABLE
        tokenRequest -> when (error) {
            "invalid_grant" -> if (status == 400) OAuthAuthorizationCheck.INVALID else OAuthAuthorizationCheck.UNAVAILABLE
            "invalid_client", "unauthorized_client", "unsupported_grant_type", "invalid_request" ->
                OAuthAuthorizationCheck.APPLICATION_REJECTED
            "invalid_scope", "insufficient_scope", "access_denied" -> OAuthAuthorizationCheck.PERMISSION_DENIED
            else -> if (status == 403) OAuthAuthorizationCheck.PERMISSION_DENIED else OAuthAuthorizationCheck.UNAVAILABLE
        }
        else -> OAuthAuthorizationCheck.UNAVAILABLE
    }

    fun callbackResult(value: String, expectedState: String): CallbackResult? {
        val url = value.toHttpUrl()
        if (url.queryParameterValues("state").singleOrNull() != expectedState) return null
        val code = url.queryParameterValues("code")
        val error = url.queryParameterValues("error")
        if (error.isEmpty() && code.size == 1) {
            return code.single()?.takeIf(String::isNotBlank)?.let { CallbackResult.Code(it) }
        }
        if (code.isEmpty() && error.size == 1) {
            return error.single()?.takeIf(String::isNotBlank)?.let { CallbackResult.Error(it) }
        }
        return null
    }

    sealed interface CallbackResult {
        class Code(val value: String) : CallbackResult
        class Error(val value: String) : CallbackResult
    }
}
