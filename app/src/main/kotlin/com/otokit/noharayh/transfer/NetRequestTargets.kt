package com.otokit.noharayh.transfer

import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import java.net.URI

/** NET owns its endpoint defaults and the contract accepted by the capture protocol. */
object NetRequestTargets {
    fun defaultUrl(target: RequestTarget): String? = when (target) {
        RequestTarget.NET_AUTH_MAIMAI -> "https://tgk-wcaime.wahlap.com/wc_auth/oauth/authorize/maimai-dx"
        RequestTarget.NET_AUTH_CHUNITHM -> "https://tgk-wcaime.wahlap.com/wc_auth/oauth/authorize/chunithm"
        RequestTarget.NET_BASE_MAIMAI -> "https://maimai.wahlap.com/maimai-mobile/"
        RequestTarget.NET_BASE_CHUNITHM -> "https://chunithm.wahlap.com/mobile/"
        else -> null
    }

    fun validate(target: RequestTarget, url: String) {
        require(defaultUrl(target) != null) { "此项不属于 NET 请求" }
        val uri = try { URI(url) } catch (_: java.net.URISyntaxException) {
            throw IllegalArgumentException("请输入完整的 HTTPS 地址")
        }
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.rawUserInfo == null &&
            uri.rawQuery == null && uri.rawFragment == null && (uri.port == -1 || uri.port in 1..65535)) {
            "请使用不含账号、查询参数或片段的 HTTPS 地址"
        }
        if (target == RequestTarget.NET_BASE_MAIMAI || target == RequestTarget.NET_BASE_CHUNITHM) {
            require(uri.rawPath.endsWith('/')) { "NET 页面根地址需要以 / 结尾" }
        }
    }

    internal fun resolve(network: NetworkSnapshot, target: RequestTarget): URI =
        URI(network.target(target, requireNotNull(defaultUrl(target))))
}
