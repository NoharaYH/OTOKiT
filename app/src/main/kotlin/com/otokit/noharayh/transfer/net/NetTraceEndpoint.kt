package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.transfer.capture.NetCaptureProtocol
import java.net.URI

/** A closed vocabulary: never copy arbitrary paths, query strings, codes or cookie values. */
internal object NetTraceEndpoint {
    private val auth = mapOf(
        "/wc_auth/oauth/authorize/maimai-dx" to "AUTH_MAIMAI",
        "/wc_auth/oauth/authorize/chunithm" to "AUTH_CHUNITHM",
        "/wc_auth/oauth/callback/maimai-dx" to "CALLBACK_MAIMAI",
        "/wc_auth/oauth/callback/chunithm" to "CALLBACK_CHUNITHM",
    )
    private val mai = listOf("", "home/", "login/", "error/", "friend/userFriendCode/", "record/",
        "record/musicGenre/search/", "record/musicSort/search/").associate { "/maimai-mobile/$it" to "MAIMAI:$it" }
    private val chuni = (listOf("", "home/", "login/", "error/", "home/playerData/", "record/playlog/", "record/worldsEndList/", "record/musicGenre/") +
        listOf("Basic", "Advanced", "Expert", "Master", "Ultima").flatMap { listOf("record/musicGenre/${it.lowercase()}", "record/musicGenre/send$it") })
        .associate { "/mobile/$it" to "CHUNITHM:$it" }

    fun classify(uri: URI): String = when (uri.host) {
        NetCaptureProtocol.AUTH_HOST -> auth[uri.path] ?: "OTHER_AUTH"
        "maimai.wahlap.com" -> mai[uri.path] ?: "OTHER_MAIMAI"
        "chunithm.wahlap.com" -> chuni[uri.path] ?: "OTHER_CHUNITHM"
        "open.weixin.qq.com" -> "WECHAT_AUTHORIZATION"
        else -> "OTHER_DESTINATION"
    }
}
