package com.otokit.noharayh.ui

import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.network.NetworkChannelCheck
import com.otokit.noharayh.network.NetworkDefaults
import com.otokit.noharayh.network.NetworkSection
import com.otokit.noharayh.network.NetworkSettings
import com.otokit.noharayh.network.ProxyScope
import com.otokit.noharayh.network.RequestTarget
import com.otokit.noharayh.ui.settings.components.ProxySettingsState
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ProxySettingsStateTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val failure = "fixture save failed"

    private class Storage {
        val fail = AtomicBoolean(false)
        private val value = AtomicReference<String?>(null)
        val settings = NetworkSettings({ value.get() }, {
            if (fail.get()) throw IOException("fixture storage unavailable")
            value.set(it)
        }).apply { savePorts(0, 0) }
    }

    /** Application interception supplies a local response before DNS or any external socket. */
    private class CheckFixture(blocked: Boolean = false) : AutoCloseable {
        val entered = CountDownLatch(1)
        private val release = CountDownLatch(if (blocked) 1 else 0)
        private val worker = AtomicReference<Thread>()
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            worker.set(Thread.currentThread())
            entered.countDown()
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw IOException("fixture check timed out")
            } catch (interrupted: InterruptedException) { throw IOException("fixture check interrupted", interrupted) }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("".toResponseBody()).build()
        }.build()
        val check = NetworkChannelCheck(client)

        fun join() {
            worker.get()?.let { thread ->
                thread.join(10_000)
                assertFalse("Fixture check did not finish", thread.isAlive)
            }
        }

        fun proceed() = release.countDown()

        override fun close() {
            release.countDown()
            join()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
        }
    }

    private fun main(action: () -> Unit) = instrumentation.runOnMainSync(action)
    private fun state(storage: Storage, fixture: CheckFixture) =
        ProxySettingsState(storage.settings, failure, "fixture invalid port", fixture.check)

    private fun completeCheck(state: ProxySettingsState) {
        val done = CountDownLatch(1)
        main {
            state.onCheckChanged = { if (state.checkReport != null) done.countDown() }
            state.startCheck(ProxyScope.NET, "https://fixture.invalid/check")
            assertFalse(state.checkCancelled)
            assertFalse(state.checkStale)
            assertNull(state.checkReport)
        }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        main {
            assertFalse(state.checking)
            assertFalse(state.checkCancelled)
            assertFalse(state.checkStale)
            assertNotNull(state.checkReport)
        }
    }

    @Test fun explicitCancellationNotifiesAndLateCompletionCannotReplaceTheCancelledState() {
        val storage = Storage()
        CheckFixture(blocked = true).use { fixture ->
            val state = state(storage, fixture)
            val notifications = AtomicInteger()
            try {
                main {
                    state.onCheckChanged = { notifications.incrementAndGet() }
                    state.startCheck(ProxyScope.NET, "https://fixture.invalid/check")
                }
                assertTrue(fixture.entered.await(10, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                var cancelledNotifications = 0
                main {
                    val before = notifications.get()
                    state.cancelCheck()
                    assertEquals(before + 1, notifications.get())
                    assertTrue(state.checkCancelled)
                    assertFalse(state.checking)
                    assertFalse(state.checkStale)
                    cancelledNotifications = notifications.get()
                }
                fixture.join()
                instrumentation.waitForIdleSync()
                main {
                    assertTrue(state.checkCancelled)
                    assertNull(state.checkReport)
                    assertEquals(cancelledNotifications, notifications.get())
                }
                fixture.proceed()
                completeCheck(state)
            } finally { main { state.close() } }
        }
    }

    @Test fun successfulSaveCancelsAnInFlightCheckAndTheNextCheckUsesTheNewRevision() {
        val storage = Storage()
        CheckFixture(blocked = true).use { fixture ->
            val state = state(storage, fixture)
            try {
                main { state.startCheck(ProxyScope.NET, "https://fixture.invalid/check") }
                assertTrue(fixture.entered.await(10, TimeUnit.SECONDS))
                main {
                    val previous = state.snapshot.revision
                    assertNull(state.savePorts("0", "0"))
                    assertTrue(state.snapshot.revision > previous)
                    assertFalse(state.checking)
                    assertTrue(state.checkCancelled)
                    assertTrue(state.checkStale)
                }
                fixture.join()
                instrumentation.waitForIdleSync()
                main { assertNull(state.checkReport) }
                fixture.proceed()
                completeCheck(state)
            } finally { main { state.close() } }
        }
    }

    @Test fun successfulSavesAndSelectiveRestoreExpireOldChecksAndBlankListenUsesDefaults() {
        val storage = Storage()
        val target = RequestTarget.NET_BASE_MAIMAI
        storage.settings.saveUrl(target, "https://fixture.invalid/records/")
        CheckFixture().use { fixture ->
            val state = state(storage, fixture)
            try {
                completeCheck(state)
                val report = state.checkReport
                main {
                    assertNull(state.saveListen("127.0.0.1", "127.0.0.2"))
                    assertSame(report, state.checkReport)
                    assertTrue(state.checkStale)
                    assertFalse(state.checkCancelled)
                    assertNull(state.saveListen("   ", ""))
                    assertEquals(NetworkDefaults.ENTRY_HOST, state.snapshot.entryHost)
                    assertEquals(NetworkDefaults.PROXY_HOST, state.snapshot.proxyHost)
                    assertNull(state.overrides.entryHost)
                    assertNull(state.overrides.proxyHost)
                }
                completeCheck(state)
                main {
                    assertNull(state.restore(setOf(NetworkSection.PORTS)))
                    assertTrue(state.checkStale)
                    assertEquals(NetworkDefaults.ENTRY_PORT, state.snapshot.entryPort)
                    assertEquals(NetworkDefaults.PROXY_PORT, state.snapshot.proxyPort)
                    assertEquals("https://fixture.invalid/records/", state.overrides.urls[target])
                }
            } finally { main { state.close() } }
        }
    }

    @Test fun failedSaveKeepsCommittedSnapshotAndRunningCheckAndCloseDoesNotNotifyTheOldPage() {
        val storage = Storage()
        CheckFixture(blocked = true).use { fixture ->
            val state = state(storage, fixture)
            val notifications = AtomicInteger()
            try {
                main {
                    state.onCheckChanged = { notifications.incrementAndGet() }
                    state.startCheck(ProxyScope.NET, "https://fixture.invalid/check")
                }
                assertTrue(fixture.entered.await(10, TimeUnit.SECONDS))
                instrumentation.waitForIdleSync()
                var afterClose = 0
                main {
                    val committed = state.snapshot
                    storage.fail.set(true)
                    assertEquals(failure, state.savePorts("13001", "13002"))
                    assertSame(committed, state.snapshot)
                    assertEquals(committed.revision, storage.settings.snapshot().revision)
                    assertTrue(state.checking)
                    assertFalse(state.checkCancelled)
                    assertFalse(state.checkStale)
                    val beforeClose = notifications.get()
                    state.close()
                    assertEquals(beforeClose, notifications.get())
                    assertFalse(state.checking)
                    assertTrue(state.checkCancelled)
                    afterClose = notifications.get()
                }
                fixture.join()
                instrumentation.waitForIdleSync()
                assertEquals(afterClose, notifications.get())
            } finally { main { state.close() } }
        }
    }
}
