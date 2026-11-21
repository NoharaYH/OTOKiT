package com.otokit.noharayh.transfer

import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.auth.OAuthProvider
import com.otokit.noharayh.network.LocalHttpsFixture
import com.otokit.noharayh.network.LocalProxyFixture
import com.otokit.noharayh.network.NetworkSnapshot
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.score.html.FileHtmlCaptures
import com.otokit.noharayh.transfer.upload.PlatformHtmlUpload
import okhttp3.Interceptor
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PlatformUploadRoutingTest {
    @get:Rule val temp = TemporaryFolder(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir)

    @Test fun bothProvidersAndGamesUploadOriginalBodiesThroughTheFrozenPlatformRoute() {
        LocalHttpsFixture { request -> LocalHttpsFixture.Response(body =
            if (request.target.startsWith("/fish/")) """{"updates":1,"creates":0}"""
            else """{"success":true,"code":200,"data":[]}""") }.use { service ->
            LocalProxyFixture().use { proxy ->
                val urls = mutableMapOf(
                    RequestTarget.OAUTH_FISH_ISSUER to service.url().removeSuffix("/"),
                    RequestTarget.OAUTH_LXNS_ISSUER to service.url().removeSuffix("/"),
                    RequestTarget.UPLOAD_FISH_MAIMAI to service.url("/fish/maimai"),
                    RequestTarget.UPLOAD_FISH_CHUNITHM to service.url("/fish/chunithm"),
                    RequestTarget.UPLOAD_LXNS_MAIMAI to service.url("/lxns/maimai"),
                    RequestTarget.UPLOAD_LXNS_CHUNITHM to service.url("/lxns/chunithm"),
                )
                val snapshot = NetworkSnapshot(revision = 81, upstream = proxy.upstream(),
                    scopes = setOf(ProxyScope.PLATFORM), urls = urls)
                val events = mutableListOf<Map<String, String>>()
                val recording = TransferRecording({ _, _, data -> events.add(data) },
                    { Interceptor { chain -> chain.proceed(chain.request()) } }, {}, {}, {})
                val archive = FileHtmlCaptures(temp.newFolder())
                val upload = PlatformHtmlUpload(archive, { provider, subject, scope ->
                    assertEquals("fixture-${provider.name}", subject)
                    assertTrue(scope in listOf("write_player", "prober.records.write", "chunithm.records.write"))
                    "fixture-access"
                }, service.client, recording, snapshot)
                // Editing the input map after task construction cannot redirect its upload.
                urls[RequestTarget.UPLOAD_LXNS_MAIMAI] = "https://must-remain-unused.invalid/upload"
                for (game in ScoreGame.entries) {
                    val ref = archive.begin(game, listOf(HtmlPageKind.PLAYER), "routing-${game.key}")
                    val body = "<html>fixture-${game.key}</html>"
                    val page = archive.save(ref, HtmlPageKind.PLAYER, body.byteInputStream()) { false }
                    for (provider in OAuthProvider.entries) {
                        val result = upload.send(provider, "fixture-${provider.name}", page) {}
                        assertEquals(if (provider == OAuthProvider.DIVING_FISH) UploadOutcome.ACCEPTED else UploadOutcome.NO_CHANGES,
                            result.outcome)
                    }
                }
                assertEquals(4, service.requests.size)
                assertEquals(setOf("/fish/maimai", "/fish/chunithm", "/lxns/maimai", "/lxns/chunithm"),
                    service.requests.map { it.target }.toSet())
                for (request in service.requests) {
                    assertEquals("POST", request.method)
                    assertEquals("Bearer fixture-access", request.header("Authorization"))
                    assertNull(request.header("Proxy-Authorization"))
                    val game = request.target.substringAfterLast('/')
                    assertEquals("<html>fixture-$game</html>", request.body.toString(Charsets.UTF_8))
                    assertEquals(request.body.size.toString(), request.header("Content-Length"))
                }
                assertEquals(4, proxy.requests.count { it.method == "CONNECT" })

                val facts = events.filter { "originalTarget" in it }
                assertEquals(4, facts.size)
                assertTrue(facts.all { it["configurationRevision"] == "81" && it["route"] == "UPSTREAM" })
                assertTrue(service.failures.toString(), service.failures.isEmpty())

                // An upstream saved for other scopes must not capture PLATFORM requests.
                val directSnapshot = NetworkSnapshot(upstream = proxy.upstream(), urls = snapshot.urls)
                val direct = PlatformHtmlUpload(archive, { _, _, _ -> "fixture-access" }, service.client,
                    network = directSnapshot)
                val ref = archive.begin(ScoreGame.MAIMAI, listOf(HtmlPageKind.PLAYER), "direct")
                val page = archive.save(ref, HtmlPageKind.PLAYER, "direct".byteInputStream()) { false }
                assertEquals(UploadOutcome.NO_CHANGES, direct.send(OAuthProvider.LXNS, "fixture", page) {}.outcome)
                assertEquals(5, service.requests.size)
                assertEquals(4, proxy.requests.count { it.method == "CONNECT" })

                val misconfigured = PlatformHtmlUpload(archive, { _, _, _ -> error("Old credentials must remain in auth") },
                    service.client, network = NetworkSnapshot(urls = mapOf(RequestTarget.UPLOAD_LXNS_MAIMAI to service.url("/leak"))))
                assertThrows(IllegalArgumentException::class.java) {
                    misconfigured.send(OAuthProvider.LXNS, "fixture", page) {}
                }
                assertEquals(5, service.requests.size)
                assertEquals(4, proxy.requests.count { it.method == "CONNECT" })
            }
        }
    }
}
