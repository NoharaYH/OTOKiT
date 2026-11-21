package com.otokit.noharayh.auth

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.network.LocalHttpsFixture
import com.otokit.noharayh.network.LocalProxyFixture
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.network.RequestTarget
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class OAuthNetworkRoutingTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val provider = OAuthProvider.LXNS

    private class Accounts : AutoCloseable {
        private val base = InstrumentationRegistry.getInstrumentation().targetContext
        private val preferenceName = "oauth-network-${UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                base.getSharedPreferences(preferenceName, mode)
        }
        override fun close() { base.deleteSharedPreferences(preferenceName) }
    }

    private fun worker(auth: OAuthConnections) = OAuthConnections::class.java.getDeclaredField("worker")
        .apply { isAccessible = true }.get(auth) as ExecutorService

    private fun close(auth: OAuthConnections) {
        worker(auth).shutdown()
        assertTrue(worker(auth).awaitTermination(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun browser(auth: OAuthConnections): String {
        val opened = CountDownLatch(1)
        val result = AtomicReference<String>()
        val failure = AtomicReference<OAuthFeedback>()
        instrumentation.runOnMainSync {
            auth.authorize(provider, { result.set(it); opened.countDown() }, { failure.set(it); opened.countDown() })
        }
        assertTrue(opened.await(15, TimeUnit.SECONDS))
        assertNull(failure.get())
        return requireNotNull(result.get())
    }

    private fun receive(auth: OAuthConnections, authorizationUrl: String) {
        val state = authorizationUrl.toHttpUrl().queryParameter("state")
        val callback = provider.redirectUri.toHttpUrl().newBuilder()
            .addQueryParameter("state", state).addQueryParameter("code", "fixture-code").build().toString()
        val done = CountDownLatch(1)
        val result = AtomicReference<OAuthFeedback>()
        instrumentation.runOnMainSync {
            assertTrue(auth.receive(callback) { receivedProvider, feedback ->
                assertEquals(provider, receivedProvider)
                result.set(feedback); done.countDown()
            })
        }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        assertEquals(OAuthFeedback.AUTHORIZED, result.get())
    }

    private fun check(auth: OAuthConnections): OAuthAuthorizationCheck {
        val done = CountDownLatch(1)
        val result = AtomicReference<OAuthAuthorizationCheck>()
        val subscription = AtomicReference<java.io.Closeable>()
        instrumentation.runOnMainSync {
            auth.checkAuthorization(provider)
            subscription.set(auth.observe {
                val value = auth.authorizationCheck(provider)
                if (value != OAuthAuthorizationCheck.CHECKING) { result.set(value); done.countDown() }
            })
        }
        assertTrue(done.await(15, TimeUnit.SECONDS))
        instrumentation.runOnMainSync { subscription.get().close() }
        return result.get()
    }

    @Test fun pendingSurvivesRecreationAndEveryOAuthRequestUsesItsPlatformRoute() {
        LocalProxyFixture("proxy-user" to "proxy-password").use { proxy ->
            LocalProxyFixture().use { changedProxy ->
                lateinit var service: LocalHttpsFixture
                service = LocalHttpsFixture { request ->
                    LocalHttpsFixture.Response(body = when (request.target) {
                        "/.well-known/openid-configuration" -> JSONObject()
                            .put("issuer", service.url().removeSuffix("/"))
                            .put("authorization_endpoint", service.url("/authorize"))
                            .put("token_endpoint", service.url("/token"))
                            .put("userinfo_endpoint", service.url("/userinfo")).toString()
                        "/token" -> """{"access_token":"fixture-access","refresh_token":"fixture-refresh","expires_in":3600,"scope":"openid profile write_player"}"""
                        "/userinfo" -> """{"sub":"fixture-subject","preferred_username":"fixture"}"""
                        else -> error("Unexpected fixture request")
                    })
                }
                service.use {
                    LocalHttpsFixture { LocalHttpsFixture.Response(500, "must remain unused") }.use { changedService ->
                        Accounts().use { accounts ->
                            val initial = NetworkSnapshot(revision = 41, upstream = proxy.upstream(),
                                scopes = setOf(ProxyScope.PLATFORM),
                                urls = mapOf(RequestTarget.OAUTH_LXNS_ISSUER to service.url().removeSuffix("/")))
                            val changed = NetworkSnapshot(revision = 42, upstream = changedProxy.upstream(),
                                scopes = setOf(ProxyScope.PLATFORM),
                                urls = mapOf(RequestTarget.OAUTH_LXNS_ISSUER to changedService.url().removeSuffix("/")))
                            val current = AtomicReference(initial)
                            val first = OAuthConnections(accounts.context, service.client, current::get)
                            val authorizationUrl = browser(first)
                            assertEquals(service.port, authorizationUrl.toHttpUrl().port)
                            assertEquals(provider.redirectUri, authorizationUrl.toHttpUrl().queryParameter("redirect_uri"))
                            current.set(changed)
                            close(first)

                            // A newly created auth owner must recover the original pending route from encrypted storage.
                            val restored = OAuthConnections(accounts.context, service.client, current::get)
                            try {
                                receive(restored, authorizationUrl)
                                assertEquals(listOf("/.well-known/openid-configuration", "/token", "/userinfo"),
                                    service.requests.map { it.target })
                                assertTrue(changedService.requests.isEmpty())
                                assertTrue(changedProxy.requests.isEmpty())
                                assertTrue(service.requests.none { it.header("Proxy-Authorization") != null })
                                assertEquals(OAuthAuthorizationCheck.INVALID, check(restored))
                                val denied = assertThrows(ScoreGrantUnavailable::class.java) {
                                    restored.scoreAccessToken(provider, "fixture-subject", "write_player", null, network = changed)
                                }
                                assertEquals(OAuthAuthorizationCheck.INVALID, denied.reason)
                                assertEquals(3, service.requests.size)
                                assertTrue(changedService.requests.isEmpty())

                                current.set(initial)
                                assertEquals(OAuthAuthorizationCheck.VALID, check(restored))
                                worker(restored).submit {
                                    val store = EncryptedOAuthStore(accounts.context)
                                    val account = requireNotNull(store.read("account_LXNS"))
                                    store.write("account_LXNS", account.put("expires", 0L))
                                }.get(10, TimeUnit.SECONDS)
                                assertEquals("fixture-access", restored.scoreAccessToken(provider,
                                    "fixture-subject", "write_player", null, network = initial))
                                val tokens = service.requests.filter { it.target == "/token" }
                                assertEquals(3, tokens.size)
                                assertEquals(1, tokens.count { it.body.toString(Charsets.UTF_8).contains("authorization_code") })
                                assertEquals(2, tokens.count { it.body.toString(Charsets.UTF_8).contains("refresh_token") })
                                assertEquals("Bearer fixture-access", service.requests.single { it.target == "/userinfo" }.header("Authorization"))
                                assertTrue(proxy.requests.count { it.method == "CONNECT" } >= 5)
                                assertTrue(proxy.requests.any { it.header("Proxy-Authorization") != null })
                                assertTrue(service.failures.toString(), service.failures.isEmpty())
                                assertTrue(changedProxy.requests.isEmpty())
                            } finally { close(restored) }
                        }
                    }
                }
            }
        }
    }

    @Test fun discoveryRejectsAnEndpointFromAnotherOriginBeforeCredentialsExist() {
        lateinit var service: LocalHttpsFixture
        service = LocalHttpsFixture { LocalHttpsFixture.Response(body = JSONObject()
            .put("issuer", service.url().removeSuffix("/"))
            .put("authorization_endpoint", "https://unrelated.invalid/authorize")
            .put("token_endpoint", service.url("/token"))
            .put("userinfo_endpoint", service.url("/userinfo")).toString()) }
        service.use {
            Accounts().use { accounts ->
                val snapshot = NetworkSnapshot(urls = mapOf(RequestTarget.OAUTH_LXNS_ISSUER to service.url().removeSuffix("/")))
                val auth = OAuthConnections(accounts.context, service.client) { snapshot }
                try {
                    val done = CountDownLatch(1)
                    val result = AtomicReference<OAuthFeedback>()
                    instrumentation.runOnMainSync {
                        auth.authorize(provider, { fail("Invalid discovery must not open a browser") }, {
                            result.set(it); done.countDown()
                        })
                    }
                    assertTrue(done.await(15, TimeUnit.SECONDS))
                    assertEquals(OAuthFeedback.LOCAL_ERROR, result.get())
                    assertEquals(1, service.requests.size)
                    assertNull(EncryptedOAuthStore(accounts.context).read("pending_LXNS"))
                } finally { close(auth) }
            }
        }
    }
}
