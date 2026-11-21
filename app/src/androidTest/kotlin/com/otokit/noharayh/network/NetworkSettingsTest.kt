package com.otokit.noharayh.network

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.storage.secure.EncryptedStringPreferences
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.security.KeyStore
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class NetworkSettingsTest {
    @Test fun overridesRestoreSelectivelyWithoutChangingDefaultsOrRunningSnapshot() {
        var saved: String? = null
        val settings = NetworkSettings({ saved }, { saved = it })
        val defaults = NetworkSnapshot.defaults()
        settings.saveUpstream(UpstreamProxy("127.0.0.1", 7890, "fixture-user", "fixture-secret"),
            setOf(ProxyScope.NET, ProxyScope.PLATFORM))
        settings.savePorts(0, 18084)
        settings.saveListen("127.0.0.1", "127.0.0.2")
        settings.saveUrl(RequestTarget.NET_AUTH_MAIMAI, "https://example.test/auth")
        val running = settings.snapshot()
        assertEquals(0, settings.overrides().entryPort)
        assertEquals(running.urls, NetworkSnapshot.fromJson(running.toJson()).urls)
        assertEquals(running.upstream, NetworkSnapshot.fromJson(running.toJson()).upstream)
        assertFalse(settings.overrides().toString().contains("fixture-secret"))
        assertFalse(running.upstream.toString().contains("fixture-secret"))
        settings.restore(setOf(NetworkSection.PORTS, NetworkSection.URLS))
        val restored = settings.snapshot()
        assertEquals(defaults.entryPort, restored.entryPort)
        assertEquals(defaults.proxyPort, restored.proxyPort)
        assertEquals("127.0.0.1", restored.entryHost)
        assertEquals(running.upstream, restored.upstream)
        assertTrue(restored.urls.isEmpty())
        assertNull(settings.overrides().entryPort)
        assertEquals(0, running.entryPort)
        assertEquals(18084, running.proxyPort)
        assertEquals("https://example.test/auth", running.urls[RequestTarget.NET_AUTH_MAIMAI])
        try {
            (running.scopes as MutableSet).clear()
            fail("A running snapshot must not expose mutable scope state")
        } catch (_: UnsupportedOperationException) { }
        try {
            (running.urls as MutableMap).clear()
            fail("A running snapshot must not expose mutable URL state")
        } catch (_: UnsupportedOperationException) { }
        settings.restore(NetworkSection.entries.toSet())
        val clean = NetworkSettings({ saved }, { saved = it })
        assertEquals(NetworkOverrides(), clean.overrides())
        assertEquals(defaults.entryHost, clean.snapshot().entryHost)
        assertEquals(defaults.proxyHost, clean.snapshot().proxyHost)
        assertEquals(defaults.entryPort, clean.snapshot().entryPort)
        assertTrue(clean.snapshot().scopes.isEmpty())
        assertNull(clean.snapshot().upstream)
        assertTrue(clean.snapshot().revision > running.revision)
    }

    @Test fun invalidInputsAndFailedPersistenceNeverReplaceConfirmedConfiguration() {
        var saved: String? = null
        var failWrite = false
        val settings = NetworkSettings({ saved }, { if (failWrite) throw IOException("fixture storage failure"); saved = it })
        settings.savePorts(0, 18084)
        val confirmed = settings.snapshot()
        val before = saved
        val invalid = listOf<() -> Unit>(
            { settings.savePorts(-1, 0) },
            { settings.savePorts(0, 65536) },
            { settings.saveListen("0.0.0.0", null) },
            { settings.saveListen("::1", null) },
            { settings.saveUpstream(UpstreamProxy("127.0.0.1:7890", 7890), setOf(ProxyScope.NET)) },
            { settings.saveUpstream(null, setOf(ProxyScope.NET)) },
            { settings.saveUrl(RequestTarget.NET_AUTH_MAIMAI, "http://example.test/") },
            { settings.saveUrl(RequestTarget.NET_AUTH_MAIMAI, "https://name:secret@example.test/") },
            { settings.saveUrl(RequestTarget.NET_AUTH_MAIMAI, "https://example.test/?token=fixture") },
            { settings.saveUrl(RequestTarget.NET_AUTH_MAIMAI, "https://example.test/#fragment") },
            { settings.saveUrl(RequestTarget.NET_BASE_MAIMAI, "https://example.test/base") },
        )
        invalid.forEach { change ->
            try { change(); fail("Invalid network configuration was accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(before, saved)
            assertEquals(confirmed.revision, settings.snapshot().revision)
        }
        val conflict = NetworkSettings({ null }, {})
        conflict.saveListen("127.0.0.1", "127.0.0.1")
        try { conflict.savePorts(18084, 18084); fail("Two listeners cannot bind the same fixed endpoint") }
        catch (_: IllegalArgumentException) { }
        failWrite = true
        try { settings.saveUpstream(UpstreamProxy("127.0.0.1", 7890), setOf(ProxyScope.NET)); fail("Storage failure was ignored") }
        catch (_: IOException) { }
        assertEquals(before, saved)
        assertNull(settings.snapshot().upstream)
        assertEquals(confirmed.revision, settings.snapshot().revision)
    }

    @Test fun proxyCredentialsUseIndependentEncryptedStorageAndRoundTripAfterReload() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "network-settings-fixture-${UUID.randomUUID()}"
        val alias = "OTOKiT.NetworkFixture.${UUID.randomUUID()}"
        try {
            val storage = EncryptedStringPreferences(context, name, alias)
            val settings = NetworkSettings({ storage.read("overrides") }, { storage.write("overrides", it) })
            settings.saveUpstream(UpstreamProxy("127.0.0.1", 7890, "fixture-user", "fixture-private-password"), setOf(ProxyScope.WECHAT))
            val encoded = context.getSharedPreferences(name, 0).getString("overrides", null)!!
            assertFalse(encoded.contains("fixture-private-password"))
            assertFalse(encoded.contains("fixture-user"))
            assertFalse(encoded.contains("upstream"))
            val reopened = EncryptedStringPreferences(context, name, alias)
            val restored = NetworkSettings({ reopened.read("overrides") }, { reopened.write("overrides", it) })
            assertEquals(settings.snapshot().upstream, restored.snapshot().upstream)
            assertEquals(settings.snapshot().scopes, restored.snapshot().scopes)
            storage.write("overrides", null)
            assertNull(reopened.read("overrides"))
        } finally {
            context.deleteSharedPreferences(name)
            KeyStore.getInstance("AndroidKeyStore").apply { load(null); deleteEntry(alias) }
        }
    }
}
