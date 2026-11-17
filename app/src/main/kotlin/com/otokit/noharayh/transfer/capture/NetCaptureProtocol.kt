package com.otokit.noharayh.transfer.capture

import java.net.URI

/** Pure protocol boundary shared by the debug proxy and the NET client. */
internal object NetCaptureProtocol {
    const val AUTH_HOST = "tgk-wcaime.wahlap.com"
    private const val CALLBACK_PREFIX = "/wc_auth/oauth/callback/"

    fun isCallback(uri: URI): Boolean = uri.scheme == "http" &&
        uri.host.equals(AUTH_HOST, ignoreCase = true) && uri.port in listOf(-1, 80) &&
        uri.rawUserInfo == null && uri.rawFragment == null &&
        uri.rawPath.startsWith(CALLBACK_PREFIX) && !uri.rawQuery.isNullOrBlank()

    fun secureCallback(uri: URI): URI {
        require(isCallback(uri)) { "Unexpected callback" }
        return URI("https://${uri.host}${uri.rawPath}?${uri.rawQuery}")
    }

    fun matchesAuthorization(callback: URI, authorization: URI): Boolean = runCatching {
        if (!isCallback(callback)) return false
        fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        val expected = query(authorization)
        val actual = query(callback)
        val redirect = URI(expected.getValue("redirect_uri"))
        callback.path == redirect.path && !actual["code"].isNullOrBlank() &&
            !expected["state"].isNullOrBlank() && actual["state"] == expected["state"]
    }.getOrDefault(false)

    fun isNetDestination(uri: URI): Boolean = uri.scheme == "https" &&
        uri.port in listOf(-1, 443) && uri.rawUserInfo == null &&
        uri.host in setOf(AUTH_HOST, "maimai.wahlap.com", "chunithm.wahlap.com")

    /** Change only the OAuth redirect parameter, preserving state and other query fields. */
    fun rewriteWechatRedirect(uri: URI): URI {
        require(uri.scheme == "https" && uri.host == "open.weixin.qq.com") { "Unexpected authorization destination" }
        var changed = false
        val query = uri.rawQuery.orEmpty().split('&').joinToString("&") { part ->
            if (!part.startsWith("redirect_uri=")) part else {
                val decoded = java.net.URLDecoder.decode(part.substringAfter('='), "UTF-8")
                val callback = URI(decoded)
                require(callback.scheme == "https" && callback.host == AUTH_HOST &&
                    callback.rawPath.startsWith(CALLBACK_PREFIX) && callback.port in listOf(-1, 443) &&
                    callback.rawUserInfo == null) { "Unexpected redirect parameter" }
                changed = true
                // Keep Wahlap's original escaping. The reference only removes the scheme's s.
                val raw = part.substringAfter('=')
                require(raw.startsWith("https")) { "Unexpected redirect encoding" }
                "redirect_uri=http" + raw.substring(5)
            }
        }
        require(changed) { "Missing redirect parameter" }
        return URI("https://${uri.rawAuthority}${uri.rawPath}?$query" +
            (uri.rawFragment?.let { "#$it" } ?: ""))
    }
}
