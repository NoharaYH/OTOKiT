package com.otokit.noharayh.transfer

import com.otokit.noharayh.transfer.capture.NetCaptureProtocol
import org.junit.Assert.*
import org.junit.Test
import java.net.URI

class NetCaptureProtocolTest {
    @Test fun rewritePreservesTheUnescapedRedirectUsedByTheReference() {
        val input = "https://open.weixin.qq.com/connect/oauth2/authorize?appid=id&redirect_uri=https://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx&state=expected#wechat_redirect"
        assertEquals(input.replace("redirect_uri=https", "redirect_uri=http"),
            NetCaptureProtocol.rewriteWechatRedirect(URI(input)).toString())
    }
    @Test fun callbackIsRestrictedToExactHostAndPath() {
        assertTrue(NetCaptureProtocol.isCallback(URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=x&state=y")))
        listOf(
            "http://tgk-wcaime.wahlap.com.evil.test/wc_auth/oauth/callback/maimai-dx?code=x",
            "http://tgk-wcaime.wahlap.com:81/wc_auth/oauth/callback/maimai-dx?code=x",
            "http://tgk-wcaime.wahlap.com/wc_auth/oauth/authorize/maimai-dx?code=x",
            "http://user@tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=x",
            "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx"
        ).forEach { assertFalse(NetCaptureProtocol.isCallback(URI(it))) }
    }

    @Test fun rewriteChangesOnlyNestedRedirectScheme() {
        val rewritten = NetCaptureProtocol.rewriteWechatRedirect(URI("https://open.weixin.qq.com/connect/oauth2/authorize?redirect_uri=https%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx&state=state%2Bvalue&appid=id#wechat_redirect"))
        assertTrue(rewritten.rawQuery.contains("redirect_uri=http%3A%2F%2F"))
        assertTrue(rewritten.rawQuery.contains("state=state%2Bvalue"))
        assertEquals("wechat_redirect", rewritten.fragment)
    }

    @Test fun secureCallbackPreservesEncodedCode() {
        assertEquals("https://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=a%2Bb&state=x",
            NetCaptureProtocol.secureCallback(URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=a%2Bb&state=x")).toString())
    }

    @Test fun callbackMustMatchOriginalStateAndGame() {
        val authorization = URI("https://open.weixin.qq.com/connect/oauth2/authorize?state=expected&redirect_uri=http%3A%2F%2Ftgk-wcaime.wahlap.com%2Fwc_auth%2Foauth%2Fcallback%2Fmaimai-dx")
        assertTrue(NetCaptureProtocol.matchesAuthorization(URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=x&state=expected"), authorization))
        assertFalse(NetCaptureProtocol.matchesAuthorization(URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?code=x&state=other"), authorization))
        assertFalse(NetCaptureProtocol.matchesAuthorization(URI("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/chunithm?code=x&state=expected"), authorization))
    }
}
