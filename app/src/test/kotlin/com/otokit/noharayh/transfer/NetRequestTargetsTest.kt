package com.otokit.noharayh.transfer

import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.transfer.capture.NetCaptureProtocol
import com.otokit.noharayh.transfer.net.NetTargetPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class NetRequestTargetsTest {
    @Test fun callbackPortMappingUsesThePinnedHttpsDestination() {
        for (port in listOf(443, 80, 8443)) {
            val destination = URI("https://mirror.invalid:$port/wc_auth/oauth/callback/maimai-dx")
            val policy = NetTargetPolicy(NetworkSnapshot(urls = mapOf(RequestTarget.NET_AUTH_MAIMAI to "https://mirror.invalid:$port/authorize")))
            val callback = URI("http://mirror.invalid:${if (port == 443) 80 else port}/wc_auth/oauth/callback/maimai-dx?code=x&state=y")
            assertTrue(NetCaptureProtocol.isCallback(callback, policy))
            val secured = NetCaptureProtocol.secureCallback(callback, policy, destination)
            assertEquals(if (port == 443) -1 else port, secured.port)
            assertEquals("code=x&state=y", secured.rawQuery)
        }
    }
    @Test fun customOriginMovesDestinationAndCallbackRulesTogether() {
        val network = NetworkSnapshot(urls = mapOf(RequestTarget.NET_AUTH_MAIMAI to "https://mirror.invalid:8443/authorize",
            RequestTarget.NET_BASE_MAIMAI to "https://mirror.invalid:8443/game/"))
        val targets = NetTargetPolicy(network)
        val original = URI("https://open.weixin.qq.com/connect/oauth2/authorize?redirect_uri=https://mirror.invalid:8443/wc_auth/oauth/callback/maimai-dx&state=expected")
        val authorization = NetCaptureProtocol.rewriteWechatRedirect(original, targets)
        val callback = URI("http://mirror.invalid:8443/wc_auth/oauth/callback/maimai-dx?code=x%2By&state=expected")
        assertTrue(NetCaptureProtocol.isNetDestination(URI("https://mirror.invalid:8443/game/page/"), targets))
        assertFalse(NetCaptureProtocol.isNetDestination(URI("https://mirror.invalid/game/page/"), targets))
        assertFalse(NetCaptureProtocol.isNetDestination(URI("https://maimai.wahlap.com/maimai-mobile/"), targets))
        assertTrue(NetCaptureProtocol.matchesAuthorization(callback, authorization, targets))
        assertFalse(NetCaptureProtocol.matchesAuthorization(URI(callback.toString().replace("8443", "8444")), authorization, targets))
        assertEquals("https://mirror.invalid:8443/wc_auth/oauth/callback/maimai-dx?code=x%2By&state=expected",
            NetCaptureProtocol.secureCallback(callback, targets).toString())
    }

    @Test fun invalidStaticUrlsAreRejectedWithoutChangingBuiltInDefaults() {
        val target = RequestTarget.NET_BASE_MAIMAI
        val builtIn = NetRequestTargets.defaultUrl(target)
        for (url in listOf("http://mirror.invalid/", "https://user:password@mirror.invalid/", "https://mirror.invalid/?code=fixed",
            "https://mirror.invalid/#fragment", "https://mirror.invalid/no-trailing-slash", "https://mirror.invalid:0/")) {
            try { NetRequestTargets.validate(target, url); fail("Accepted $url") }
            catch (_: IllegalArgumentException) { }
        }
        NetRequestTargets.validate(target, "https://mirror.invalid:8443/game/")
        assertEquals(builtIn, NetRequestTargets.defaultUrl(target))
    }
}
