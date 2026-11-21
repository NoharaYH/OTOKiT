package com.otokit.noharayh.auth

import com.otokit.noharayh.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class ProviderOAuthTest {
    private val callback = OAuthProvider.DIVING_FISH.redirectUri

    @Test fun pkceUsesTheS256ChallengeFromRfc7636() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            ProviderOAuth.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"))
    }

    @Test fun onlyConfiguredHttpsPathsAreAccepted() {
        assertEquals(OAuthProvider.DIVING_FISH, ProviderOAuth.callbackProvider(callback))
        assertEquals(OAuthProvider.LXNS, ProviderOAuth.callbackProvider(OAuthProvider.LXNS.redirectUri))
        assertNull(ProviderOAuth.callbackProvider(callback.replace("https:", "http:")))
        assertNull(ProviderOAuth.callbackProvider(callback.replace(BuildConfig.OAUTH_CALLBACK_HOST, "wrong.invalid")))
        assertNull(ProviderOAuth.callbackProvider("$callback/"))
        assertNull(ProviderOAuth.callbackProvider("$callback#anything"))
    }

    @Test fun callbackMustMatchAUniqueStateAndOneOutcome() {
        val result = ProviderOAuth.callbackResult("$callback?code=a%2Bb&state=valid", "valid")
            as ProviderOAuth.CallbackResult.Code
        assertEquals("a+b", result.value)
        val denied = ProviderOAuth.callbackResult("$callback?error=access_denied&state=valid", "valid")
            as ProviderOAuth.CallbackResult.Error
        assertEquals("access_denied", denied.value)
        assertNull(ProviderOAuth.callbackResult("$callback?code=a&state=wrong", "valid"))
        assertNull(ProviderOAuth.callbackResult("$callback?code=a&state=valid&state=valid", "valid"))
        assertNull(ProviderOAuth.callbackResult("$callback?code=a&code=b&state=valid", "valid"))
        assertNull(ProviderOAuth.callbackResult("$callback?code=a&error=access_denied&state=valid", "valid"))
    }

    @Test fun authorizationCheckDistinguishesRevocationFromOtherFailures() {
        assertEquals(OAuthAuthorizationCheck.INVALID, ProviderOAuth.checkFailure(400, "invalid_grant", true))
        assertEquals(OAuthAuthorizationCheck.PERMISSION_DENIED, ProviderOAuth.checkFailure(400, "invalid_scope", true))
        assertEquals(OAuthAuthorizationCheck.APPLICATION_REJECTED, ProviderOAuth.checkFailure(401, "invalid_client", true))
        assertEquals(OAuthAuthorizationCheck.PERMISSION_DENIED, ProviderOAuth.checkFailure(403, null, true))
        assertEquals(OAuthAuthorizationCheck.UNAVAILABLE, ProviderOAuth.checkFailure(403, null, false))
        assertEquals(OAuthAuthorizationCheck.RATE_LIMITED, ProviderOAuth.checkFailure(429, null, true))
        assertEquals(OAuthAuthorizationCheck.SERVICE_UNAVAILABLE, ProviderOAuth.checkFailure(503, null, true))
        assertEquals(OAuthAuthorizationCheck.UNAVAILABLE, ProviderOAuth.checkFailure(400, null, true))
    }

    @Test fun customIssuerKeepsHttpsAndDiscoveredEndpointOriginConstraints() {
        val provider = OAuthProvider.LXNS
        assertEquals("https://custom.example/oidc", provider.validateIssuer(" https://custom.example/oidc/ "))
        for (invalid in listOf("http://custom.example", "https://user:secret@custom.example",
            "https://custom.example?token=secret", "https://custom.example#fragment")) {
            assertThrows(IllegalArgumentException::class.java) { provider.validateIssuer(invalid) }
        }
        val issuer = "https://custom.example/oidc"
        assertEquals("https://custom.example/token", ProviderOAuth.endpoint(issuer, "https://custom.example/token").toString())
        for (invalid in listOf("https://other.example/token", "https://custom.example:444/token",
            "http://custom.example/token", "https://user@custom.example/token", "https://custom.example/token#secret")) {
            assertThrows(IllegalArgumentException::class.java) { ProviderOAuth.endpoint(issuer, invalid) }
        }
    }
}
