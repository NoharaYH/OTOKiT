package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.network.*
import com.otokit.noharayh.ui.kit.common.dialog.MessageDialog
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.settings.components.ProxySettingsContent
import com.otokit.noharayh.ui.settings.components.ProxySettingsState
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ProxySettingsTest : UiComponentTestHost() {
    private var persisted: String? = null
    private var failWrites = false
    private lateinit var settings: NetworkSettings
    private lateinit var state: ProxySettingsState
    private var content: ProxySettingsContent? = null

    @Before fun settingsFixture() {
        settings = NetworkSettings({ persisted }, { if (failWrites) throw IOException("fixture"); persisted = it })
    }

    @After fun closeSettings() { main { content?.close() } }

    @Test fun sixEntriesKeepCancelledDraftsPrivateAndScopesIndependent() {
        val mounted = open()
        main {
            val labels = listOf(R.string.settings_proxy_upstream, R.string.settings_proxy_ports, R.string.settings_proxy_listen,
                R.string.settings_proxy_check, R.string.settings_proxy_restore, R.string.settings_proxy_urls)
            assertEquals(labels.map(activity::getString), mounted.view.allViews
                .filter { it.contentDescription in labels.map(activity::getString) }.map { it.contentDescription.toString() }.toList())
            action(mounted.view, R.string.settings_proxy_upstream).performClick()
            val draft = dialog(mounted)!!
            edit(draft, R.string.settings_proxy_host).setText("127.0.0.1")
            edit(draft, R.string.settings_proxy_port).setText("43210")
            assertTrue(draft.window!!.decorView.allViews.filterIsInstance<GroupedSwitchRow>().none { it.isChecked })
            action(draft.window!!.decorView, R.string.settings_proxy_cancel).performClick()
            assertNull(settings.snapshot().upstream)
            assertTrue(settings.snapshot().scopes.isEmpty())
            action(mounted.view, R.string.settings_proxy_upstream).performClick()
            val next = dialog(mounted)!!
            assertEquals("", edit(next, R.string.settings_proxy_host).text.toString())
            edit(next, R.string.settings_proxy_host).setText("127.0.0.1")
            edit(next, R.string.settings_proxy_port).setText("43210")
            action(next.window!!.decorView, R.string.settings_proxy_scope_net).performClick()
            action(next.window!!.decorView, R.string.settings_proxy_save).performClick()
            assertEquals(setOf(ProxyScope.NET), settings.snapshot().scopes)
            assertEquals(43210, settings.snapshot().upstream!!.port)
        }
        val reopened = NetworkSettings({ persisted }, { persisted = it })
        assertEquals(setOf(ProxyScope.NET), reopened.snapshot().scopes)
        assertEquals("127.0.0.1", reopened.snapshot().upstream!!.host)
    }

    @Test fun invalidPortsAndStorageFailureKeepTheEditorAndItsDraft() {
        val mounted = open()
        main {
            action(mounted.view, R.string.settings_proxy_ports).performClick()
            val editor = dialog(mounted)!!
            edit(editor, R.string.settings_proxy_entry_port).setText("99999")
            action(editor.window!!.decorView, R.string.settings_proxy_save).performClick()
            assertTrue(editor.isShowing)
            assertEquals("99999", edit(editor, R.string.settings_proxy_entry_port).text.toString())
            assertNull(settings.overrides().entryPort)
            edit(editor, R.string.settings_proxy_entry_port).setText("0")
            failWrites = true
            action(editor.window!!.decorView, R.string.settings_proxy_save).performClick()
            assertTrue(editor.isShowing)
            assertTrue(editor.window!!.decorView.allViews.filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.settings_proxy_save_failed) })
            assertNull(settings.overrides().entryPort)
            failWrites = false
            action(editor.window!!.decorView, R.string.settings_proxy_save).performClick()
            assertFalse(editor.isShowing)
            assertEquals(0, settings.snapshot().entryPort)
        }
    }

    @Test fun defaultsStayReadOnlyAndSelectiveRestoreKeepsUnselectedSections() {
        val target = RequestTarget.NET_BASE_MAIMAI
        settings.saveUpstream(UpstreamProxy("127.0.0.1", 43210), setOf(ProxyScope.WECHAT))
        settings.savePorts(0, 0)
        settings.saveListen("127.0.0.1", "127.0.0.2")
        settings.saveUrl(target, "https://example.invalid/records/")
        val mounted = open()
        val originalDefault = state.defaultUrl(target)
        main {
            action(mounted.view, R.string.settings_proxy_urls).performClick()
            action(dialog(mounted)!!.window!!.decorView, R.string.settings_proxy_url_net_base_mai).performClick()
            val editor = dialog(mounted)!!
            assertEquals(1, editor.window!!.decorView.allViews.filterIsInstance<EditText>().count())
            assertTrue(editor.window!!.decorView.allViews.filterIsInstance<TextView>().any { it.text.toString() == originalDefault })
            edit(editor, R.string.settings_proxy_url_override).setText("")
            action(editor.window!!.decorView, R.string.settings_proxy_save).performClick()
            assertFalse(target in settings.overrides().urls)
            assertEquals(originalDefault, state.defaultUrl(target))
            settings.saveUrl(target, "https://example.invalid/records/")
            action(mounted.view, R.string.settings_proxy_restore).performClick()
            var restore = dialog(mounted)!!
            action(restore.window!!.decorView, R.string.settings_proxy_ports).performClick()
            action(restore.window!!.decorView, R.string.settings_proxy_restore_confirm).performClick()
            assertNull(settings.overrides().entryPort)
            assertEquals("127.0.0.1", settings.overrides().entryHost)
            assertEquals(setOf(ProxyScope.WECHAT), settings.snapshot().scopes)
            assertTrue(target in settings.overrides().urls)
            action(mounted.view, R.string.settings_proxy_restore).performClick()
            restore = dialog(mounted)!!
            action(restore.window!!.decorView, R.string.settings_proxy_restore_all).performClick()
            assertTrue(restore.window!!.decorView.allViews.filterIsInstance<GroupedSwitchRow>().all { it.isChecked })
            action(restore.window!!.decorView, R.string.settings_proxy_restore_confirm).performClick()
            assertEquals(NetworkOverrides(), settings.overrides())
            assertEquals(originalDefault, state.defaultUrl(target))
        }
    }

    @Test fun channelCheckUsesNoAccountCredentialsAndClosesWithThePage() {
        settings.savePorts(0, 0)
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor {
            val request = it.request()
            assertEquals("HEAD", request.method)
            assertNull(request.header("Authorization"))
            assertNull(request.header("Cookie"))
            calls.incrementAndGet()
            try { release.await(3, TimeUnit.SECONDS) }
            catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Fixture request cancelled").apply { initCause(interrupted) }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("".toResponseBody()).build()
        }.build()
        val mounted = open(NetworkChannelCheck(client))
        try {
            main {
                action(mounted.view, R.string.settings_proxy_check).performClick()
                val checking = dialog(mounted)!!
                edit(checking, R.string.settings_proxy_check_target).setText("https://example.invalid/check")
                action(checking.window!!.decorView, R.string.settings_proxy_check_start).performClick()
            }
            awaitMain { calls.get() == 1 }
            main {
                assertTrue(state.checking)
                assertFalse(action(dialog(mounted)!!.window!!.decorView, R.string.settings_proxy_check_running).isEnabled)
                mounted.close()
                assertFalse(state.checking)
                assertNull(dialog(mounted))
                assertNull(state.onCheckChanged)
            }
        } finally { release.countDown() }
    }

    private fun open(check: NetworkChannelCheck = NetworkChannelCheck()): ProxySettingsContent {
        lateinit var mounted: ProxySettingsContent
        main {
            state = ProxySettingsState(settings, activity.getString(R.string.settings_proxy_save_failed),
                activity.getString(R.string.settings_proxy_invalid_port), check)
            mounted = ProxySettingsContent(activity, state)
            content = mounted
        }
        mount(mounted.view, activity.resources.displayMetrics.widthPixels - 160, -1)
        return mounted
    }

    private fun dialog(content: ProxySettingsContent) = ProxySettingsContent::class.java
        .getDeclaredField("dialog").apply { isAccessible = true }.get(content) as MessageDialog?
    private fun action(view: View, label: Int) = view.allViews.single { it.contentDescription == activity.getString(label) }
    private fun edit(dialog: MessageDialog, label: Int) = dialog.window!!.decorView.allViews.filterIsInstance<EditText>()
        .single { it.contentDescription == activity.getString(label) }
    private fun awaitMain(condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + 3000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < end)
        fail("The proxy settings state did not arrive")
    }
}
