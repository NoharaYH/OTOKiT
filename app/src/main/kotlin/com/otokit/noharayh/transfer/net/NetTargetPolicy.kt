package com.otokit.noharayh.transfer.net

import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.transfer.NetRequestTargets
import java.net.URI

/** Frozen origins used by both callback capture and NET HTTP; no global allow-list mutation. */
internal class NetTargetPolicy(network: NetworkSnapshot = NetworkSnapshot.defaults()) {
    private val authorization = listOf(RequestTarget.NET_AUTH_MAIMAI, RequestTarget.NET_AUTH_CHUNITHM)
        .map { NetRequestTargets.resolve(network, it) }
    private val destinations = authorization + listOf(RequestTarget.NET_BASE_MAIMAI, RequestTarget.NET_BASE_CHUNITHM)
        .map { NetRequestTargets.resolve(network, it) }

    fun isDestination(uri: URI): Boolean = uri.scheme == "https" && uri.rawUserInfo == null &&
        uri.rawFragment == null && destinations.any { sameOrigin(uri, it) }

    fun isCallback(uri: URI): Boolean = uri.scheme == "http" && uri.rawUserInfo == null &&
        uri.rawFragment == null && uri.rawPath.startsWith("/wc_auth/oauth/callback/") &&
        !uri.rawQuery.isNullOrBlank() && authorization.any {
            uri.host.equals(it.host, true) && (if (uri.port == -1) 80 else uri.port) in
                (if (it.port == -1 || it.port == 443) setOf(80, 443) else setOf(it.port))
        }

    fun secureCallback(uri: URI, expected: URI? = null): URI {
        require(isCallback(uri)) { "Unexpected callback" }
        val origin = expected?.also { require(isSecureCallback(it) && it.host.equals(uri.host, true) && it.rawPath == uri.rawPath) }
            ?: authorization.first { auth -> auth.host.equals(uri.host, true) &&
                (if (uri.port == -1) 80 else uri.port) in
                (if (auth.port == -1 || auth.port == 443) setOf(80, 443) else setOf(auth.port)) }
        val authority = if (origin.port == 443) origin.rawAuthority.removeSuffix(":443") else origin.rawAuthority
        return URI("https://$authority${uri.rawPath}?${uri.rawQuery}")
    }

    fun isSecureCallback(uri: URI): Boolean = uri.scheme == "https" && uri.rawUserInfo == null &&
        uri.rawFragment == null && uri.rawPath.startsWith("/wc_auth/oauth/callback/") &&
        authorization.any { sameOrigin(uri, it) }

    companion object {
        fun sameOrigin(first: URI, second: URI): Boolean = first.scheme == second.scheme &&
            first.host.equals(second.host, true) &&
            (if (first.port == -1) 443 else first.port) == (if (second.port == -1) 443 else second.port)
    }
}
