package com.otokit.noharayh.ui

import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.core.view.allViews
import com.otokit.noharayh.R
import com.otokit.noharayh.network.*
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsSection
import com.otokit.noharayh.ui.kit.settings.SettingsCard
import com.otokit.noharayh.ui.kit.settings.SettingsHeaderStatus
import com.otokit.noharayh.ui.kit.settings.ThemeSelectorPanel
import com.otokit.noharayh.ui.kit.settings.ThemeDropdownMenu
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

    @Test fun upstreamSavedHeaderRequiresSuccessfulWriteAndClearsWhenEdited() {
        val mounted = open(ProxySettingsSection.UPSTREAM)
        lateinit var saved: SettingsHeaderStatus
        main {
            saved = mounted.view.allViews.filterIsInstance<SettingsHeaderStatus>().single()
            assertFalse(saved.isShown)
            edit(mounted, R.string.settings_proxy_host).setText("127.0.0.1")
            edit(mounted, R.string.settings_proxy_port).setText("43210")
            failWrites = true
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertFalse(saved.isShown)
            assertNull(settings.snapshot().upstream)
            failWrites = false
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertEquals(43210, settings.snapshot().upstream!!.port)
        }
        awaitMain { saved.isShown && saved.alpha == 1f }
        main {
            val label = saved.allViews.filterIsInstance<TextView>().single()
            assertEquals(activity.getString(R.string.settings_proxy_config_saved), label.text.toString())
            edit(mounted, R.string.settings_proxy_host).setText("draft.invalid")
        }
        awaitMain { !saved.isShown }
        assertEquals("127.0.0.1", settings.snapshot().upstream!!.host)
    }

    @Test fun sixPagesOnlyContainTheirOwnInlineEditors() {
        val expected = mapOf(ProxySettingsSection.UPSTREAM to 4, ProxySettingsSection.PORTS to 2,
            ProxySettingsSection.LISTEN to 2, ProxySettingsSection.CHECK to 1,
            ProxySettingsSection.RESTORE to 0, ProxySettingsSection.URLS to 10)
        for ((section, count) in expected) {
            val mounted = open(section)
            main {
                assertEquals(count, mounted.view.allViews.filterIsInstance<EditText>().count())
                assertFalse(mounted.view.allViews.any { it is ThemeSelectorPanel || it is ThemeDropdownMenu })
                mounted.view.allViews.filterIsInstance<SettingsCard>().forEach { card ->
                    val header = card.getChildAt(0) as ViewGroup
                    assertTrue("三级容器标题从文字开始，不留图标槽位", header.getChildAt(0) is TextView)
                    assertEquals(0, (header.getChildAt(0).layoutParams as android.widget.LinearLayout.LayoutParams).marginStart)
                }
            }
        }
    }

    @Test fun leavingAnUpstreamDraftDoesNotSaveAndScopesStayIndependent() {
        var mounted = open(ProxySettingsSection.UPSTREAM)
        main {
            edit(mounted, R.string.settings_proxy_host).setText("127.0.0.1")
            edit(mounted, R.string.settings_proxy_port).setText("43210")
            assertTrue(mounted.view.allViews.filterIsInstance<GroupedSwitchRow>().none { it.isChecked })
            mounted.close()
        }
        assertNull(settings.snapshot().upstream)
        mounted = open(ProxySettingsSection.UPSTREAM)
        main {
            assertEquals("", edit(mounted, R.string.settings_proxy_host).text.toString())
            edit(mounted, R.string.settings_proxy_host).setText("127.0.0.1")
            edit(mounted, R.string.settings_proxy_port).setText("43210")
            action(mounted.view, R.string.settings_proxy_scope_net).performClick()
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertEquals(setOf(ProxyScope.NET), settings.snapshot().scopes)
        }
        val reopened = NetworkSettings({ persisted }, { persisted = it })
        assertEquals(43210, reopened.snapshot().upstream!!.port)
        assertEquals(setOf(ProxyScope.NET), reopened.snapshot().scopes)
    }

    @Test fun invalidPortsAndStorageFailureKeepInlineDrafts() {
        val mounted = open(ProxySettingsSection.PORTS)
        lateinit var saved: SettingsHeaderStatus
        main {
            saved = mounted.view.allViews.filterIsInstance<SettingsHeaderStatus>().single()
            edit(mounted, R.string.settings_proxy_entry_port).setText("99999")
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertEquals("99999", edit(mounted, R.string.settings_proxy_entry_port).text.toString())
            assertNull(settings.overrides().entryPort)
            edit(mounted, R.string.settings_proxy_entry_port).setText("0")
            failWrites = true
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertTrue(mounted.view.allViews.filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.settings_proxy_save_failed) })
            assertNull(settings.overrides().entryPort)
            assertFalse(saved.isShown)
            failWrites = false
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertEquals(0, settings.snapshot().entryPort)
        }
        awaitMain { saved.isShown && saved.alpha == 1f }
        main {
            edit(mounted, R.string.settings_proxy_entry_port).setText("18084")
            assertEquals(0, settings.snapshot().entryPort)
        }
        awaitMain { !saved.isShown }
    }

    @Test fun defaultsStayReadOnlyAndUrlsAreSavedIndividually() {
        val mounted = open(ProxySettingsSection.URLS)
        main {
            val cards = mounted.view.allViews.filterIsInstance<SettingsCard>().toList()
            assertEquals(10, cards.size)
            assertEquals(10, mounted.view.allViews.filterIsInstance<TextView>()
                .count { it.contentDescription == activity.getString(R.string.settings_proxy_url_default) && it !is EditText })
            val card = cards.single { card -> card.allViews.filterIsInstance<TextView>()
                .any { it.text.toString() == activity.getString(R.string.settings_proxy_url_net_base_mai) } }
            val value = card.allViews.filterIsInstance<EditText>().single()
            assertEquals("", value.text.toString())
            val original = state.defaultUrl(RequestTarget.NET_BASE_MAIMAI)
            value.setText("https://fixture.invalid/maimai/")
            action(card, R.string.settings_proxy_save).performClick()
            assertEquals(mapOf(RequestTarget.NET_BASE_MAIMAI to "https://fixture.invalid/maimai/"), settings.snapshot().urls)
            assertEquals(original, state.defaultUrl(RequestTarget.NET_BASE_MAIMAI))
            value.setText("")
            action(card, R.string.settings_proxy_save).performClick()
            assertTrue(settings.snapshot().urls.isEmpty())
            assertEquals(original, state.defaultUrl(RequestTarget.NET_BASE_MAIMAI))
        }
    }

    @Test fun restorePageSelectsSectionsAndAllWithoutChangingOtherGroups() {
        settings.saveUpstream(UpstreamProxy("127.0.0.1", 43210), setOf(ProxyScope.WECHAT))
        settings.savePorts(0, 0)
        settings.saveListen("127.0.0.1", "127.0.0.2")
        settings.saveUrl(RequestTarget.NET_BASE_MAIMAI, "https://fixture.invalid/maimai/")
        val mounted = open(ProxySettingsSection.RESTORE)
        main {
            action(mounted.view, R.string.settings_proxy_ports).performClick()
            action(mounted.view, R.string.settings_proxy_restore_confirm).performClick()
            assertNull(settings.overrides().entryPort)
            assertEquals("127.0.0.1", settings.overrides().entryHost)
            assertTrue(settings.snapshot().urls.isNotEmpty())
            assertEquals(setOf(ProxyScope.WECHAT), settings.snapshot().scopes)
            action(mounted.view, R.string.settings_proxy_restore_all).performClick()
            assertTrue(mounted.view.allViews.filterIsInstance<GroupedSwitchRow>().all { it.isChecked })
            action(mounted.view, R.string.settings_proxy_restore_confirm).performClick()
            assertEquals(NetworkOverrides(), settings.overrides())
        }
    }

    @Test fun listenPageValidatesAndEmptyInputsUseBuiltInDefaults() {
        val mounted = open(ProxySettingsSection.LISTEN)
        main {
            edit(mounted, R.string.settings_proxy_entry_host).setText("0.0.0.0")
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertNull(settings.overrides().entryHost)
            assertEquals("0.0.0.0", edit(mounted, R.string.settings_proxy_entry_host).text.toString())
            edit(mounted, R.string.settings_proxy_entry_host).setText(" ")
            edit(mounted, R.string.settings_proxy_listen_host).setText("")
            action(mounted.view, R.string.settings_proxy_save).performClick()
            assertNull(settings.overrides().entryHost)
            assertNull(settings.overrides().proxyHost)
        }
    }

    @Test fun channelCheckIsInlineAndPageExitCancelsItsCredentialFreeRequest() {
        settings.savePorts(0, 0)
        val calls = AtomicInteger()
        val release = CountDownLatch(1)
        val client = OkHttpClient.Builder().addInterceptor {
            assertEquals("HEAD", it.request().method)
            assertNull(it.request().header("Authorization"))
            assertNull(it.request().header("Cookie"))
            calls.incrementAndGet()
            try { release.await(3, TimeUnit.SECONDS) }
            catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                throw InterruptedIOException("Fixture request cancelled").apply { initCause(interrupted) }
            }
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("".toResponseBody()).build()
        }.build()
        val mounted = open(ProxySettingsSection.CHECK, NetworkChannelCheck(client))
        try {
            main {
                edit(mounted, R.string.settings_proxy_check_target).setText("https://example.invalid/check")
                action(mounted.view, R.string.settings_proxy_check_start).performClick()
            }
            awaitMain { calls.get() == 1 }
            main {
                assertTrue(state.checking)
                action(mounted.view, R.string.settings_proxy_check_cancel).performClick()
                assertFalse(state.checking)
                assertTrue(state.checkCancelled)
            }
            awaitMain { mounted.view.allViews.any {
                it.contentDescription == activity.getString(R.string.settings_proxy_check_cancelled)
            } }
            main {
                mounted.close()
                assertNull(state.onCheckChanged)
            }
        } finally { release.countDown() }
    }

    @Test fun completedCheckStaysBoundToItsOriginalScopeAndTarget() {
        settings.savePorts(0, 0)
        val before = persisted
        val client = OkHttpClient.Builder().addInterceptor {
            Response.Builder().request(it.request()).protocol(Protocol.HTTP_1_1).code(200).message("fixture")
                .body("".toResponseBody()).build()
        }.build()
        val mounted = open(ProxySettingsSection.CHECK, NetworkChannelCheck(client))
        main {
            edit(mounted, R.string.settings_proxy_check_target).setText("https://example.invalid/first")
            action(mounted.view, R.string.settings_proxy_check_start).performClick()
        }
        awaitMain { state.checkReport != null }
        main {
            assertEquals(ProxyScope.NET, state.checkReport!!.scope)
            action(mounted.view, R.string.settings_proxy_scope_platform).performClick()
        }
        awaitMain { mounted.view.allViews.any {
            it.contentDescription == activity.getString(R.string.settings_proxy_check_stale)
        } }
        main {
            edit(mounted, R.string.settings_proxy_check_target).setText("https://example.invalid/second")
            assertEquals("https://example.invalid/first", state.checkReport!!.targetUrl)
            assertEquals(ProxyScope.NET, state.checkReport!!.scope)
            action(mounted.view, R.string.settings_proxy_check_stale).performClick()
        }
        awaitMain { state.checkReport?.scope == ProxyScope.PLATFORM }
        main { assertEquals("https://example.invalid/second", state.checkReport!!.targetUrl) }
        assertEquals(before, persisted)
    }

    private fun open(section: ProxySettingsSection, check: NetworkChannelCheck = NetworkChannelCheck()): ProxySettingsContent {
        lateinit var mounted: ProxySettingsContent
        main {
            content?.close()
            host.removeAllViews()
            state = ProxySettingsState(settings, activity.getString(R.string.settings_proxy_save_failed),
                activity.getString(R.string.settings_proxy_invalid_port), check)
            mounted = ProxySettingsContent(activity, state, section)
            content = mounted
        }
        mount(mounted.view, activity.resources.displayMetrics.widthPixels - 160, -1)
        return mounted
    }

    private fun action(view: View, label: Int) = view.allViews.single { it.contentDescription == activity.getString(label) }
    private fun edit(content: ProxySettingsContent, label: Int) = content.view.allViews.filterIsInstance<EditText>()
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
