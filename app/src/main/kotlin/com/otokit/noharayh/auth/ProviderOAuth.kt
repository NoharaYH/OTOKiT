package com.otokit.noharayh.auth

import com.otokit.noharayh.BuildConfig
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
    internal val writes: List<String>,
) {
    DIVING_FISH(
        "https://auth.diving-fish.com",
        BuildConfig.DIVING_FISH_CLIENT_ID,
        BuildConfig.DIVING_FISH_CALLBACK_PATH,
        listOf("prober.records.write", "chunithm.records.write"),
    ),
    LXNS(
        "https://maimai.lxns.net",
        BuildConfig.LXNS_CLIENT_ID,
        BuildConfig.LXNS_CALLBACK_PATH,
        listOf("write_player"),
    );

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
