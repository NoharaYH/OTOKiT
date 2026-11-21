package com.otokit.noharayh.transfer.capture

import java.net.URI
import com.otokit.noharayh.transfer.net.NetTargetPolicy

/** Pure protocol boundary shared by the debug proxy and the NET client. */
internal object NetCaptureProtocol {
    const val AUTH_HOST = "tgk-wcaime.wahlap.com"
    private val defaults = NetTargetPolicy()

    fun isCallback(uri: URI, targets: NetTargetPolicy = defaults): Boolean = targets.isCallback(uri)

    fun secureCallback(uri: URI, targets: NetTargetPolicy = defaults, expected: URI? = null): URI =
        targets.secureCallback(uri, expected)

    fun matchesAuthorization(callback: URI, authorization: URI, targets: NetTargetPolicy = defaults): Boolean = runCatching {
        if (!isCallback(callback, targets)) return false
        fun query(uri: URI): Map<String, String> = uri.rawQuery.orEmpty().split('&').associate { part ->
            val pair = part.split('=', limit = 2)
            java.net.URLDecoder.decode(pair[0], "UTF-8") to java.net.URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        val expected = query(authorization)
        val actual = query(callback)
        val redirect = URI(expected.getValue("redirect_uri"))
        callback.scheme == redirect.scheme && callback.host.equals(redirect.host, true) &&
            (if (callback.port == -1) 80 else callback.port) == (if (redirect.port == -1) 80 else redirect.port) &&
            callback.path == redirect.path && !actual["code"].isNullOrBlank() &&
            !expected["state"].isNullOrBlank() && actual["state"] == expected["state"]
    }.getOrDefault(false)

    fun isNetDestination(uri: URI, targets: NetTargetPolicy = defaults): Boolean = targets.isDestination(uri)

    /** Change only the OAuth redirect parameter, preserving state and other query fields. */
    fun rewriteWechatRedirect(uri: URI, targets: NetTargetPolicy = defaults): URI {
        require(uri.scheme == "https" && uri.host == "open.weixin.qq.com") { "Unexpected authorization destination" }
        var changed = false
        val query = uri.rawQuery.orEmpty().split('&').joinToString("&") { part ->
            if (!part.startsWith("redirect_uri=")) part else {
                val decoded = java.net.URLDecoder.decode(part.substringAfter('='), "UTF-8")
                val callback = URI(decoded)
                require(targets.isSecureCallback(callback)) { "Unexpected redirect parameter" }
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
