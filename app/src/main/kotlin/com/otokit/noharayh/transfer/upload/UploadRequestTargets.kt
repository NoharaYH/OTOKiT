package com.otokit.noharayh.transfer.upload

import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** 上传业务的默认目标与配置边界；代理路径由 network 决定。 */
object UploadRequestTargets {
    fun defaultUrl(target: RequestTarget): String? = when (target) {
        RequestTarget.UPLOAD_FISH_MAIMAI -> "https://www.diving-fish.com/api/maimaidxprober/player/update_records_html"
        RequestTarget.UPLOAD_FISH_CHUNITHM -> "https://www.diving-fish.com/api/chunithmprober/player/update_records_html"
        RequestTarget.UPLOAD_LXNS_MAIMAI -> "https://maimai.lxns.net/api/v0/user/maimai/player/html"
        RequestTarget.UPLOAD_LXNS_CHUNITHM -> "https://maimai.lxns.net/api/v0/user/chunithm/player/html"
        else -> null
    }

    fun validate(target: RequestTarget, value: String) {
        require(defaultUrl(target) != null) { "这不是成绩上传地址。" }
        val url = value.trim().toHttpUrlOrNull()
        require(url != null && url.isHttps && url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty() &&
            url.query == null && url.fragment == null) { "成绩上传地址须为 HTTPS，且不能包含账号、密码、查询参数或片段。" }
    }

    /** Upload credentials may only go to the platform's existing origin or a coherently reauthorized issuer. */
    fun validateRoute(snapshot: NetworkSnapshot, provider: OAuthProvider, mai: Boolean) {
        val upload = url(snapshot, provider, mai)
        val issuer = provider.validateIssuer(snapshot.target(provider.issuerTarget, provider.defaultIssuer))
        val expected = if (issuer == provider.defaultIssuer) {
            requireNotNull(defaultUrl(key(provider, mai))).toHttpUrl()
        } else issuer.toHttpUrl()
        require(upload.scheme == expected.scheme && upload.host == expected.host && upload.port == expected.port) {
            if (issuer == provider.defaultIssuer) "默认授权仅可上传到平台原有域名。更换上传域名前，请先修改授权服务地址并重新授权。"
            else "自定义上传地址须与自定义授权服务地址使用相同的协议、域名和端口，并重新授权。"
        }
    }

    internal fun key(provider: OAuthProvider, mai: Boolean): RequestTarget = when (provider) {
        OAuthProvider.DIVING_FISH -> if (mai) RequestTarget.UPLOAD_FISH_MAIMAI else RequestTarget.UPLOAD_FISH_CHUNITHM
        OAuthProvider.LXNS -> if (mai) RequestTarget.UPLOAD_LXNS_MAIMAI else RequestTarget.UPLOAD_LXNS_CHUNITHM
    }

    internal fun url(snapshot: NetworkSnapshot, provider: OAuthProvider, mai: Boolean): HttpUrl {
        val target = key(provider, mai)
        val value = snapshot.target(target, requireNotNull(defaultUrl(target)))
        validate(target, value)
        return value.toHttpUrl()
    }
}
