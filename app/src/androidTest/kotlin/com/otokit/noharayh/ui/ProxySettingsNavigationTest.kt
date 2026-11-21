package com.otokit.noharayh.ui

import android.graphics.Rect
import android.os.SystemClock
import android.view.View
import androidx.activity.BackEventCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.allViews
import com.otokit.noharayh.MainActivity
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.kit.settings.SettingsButton
import com.otokit.noharayh.ui.settings.SettingsAssembly
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsPage
import com.otokit.noharayh.ui.settings.subPages.ProxySettingsSection
import org.junit.Assert.*
import org.junit.Test

class ProxySettingsNavigationTest : UiComponentTestHost() {
    @Test fun sixEntriesOpenIndependentPagesAndReturnToTheirSection() {
        val assembly = openSyncSettings()
        for (section in ProxySettingsSection.entries) {
            val entry = openProxy(assembly, section)
            main {
                val page = assembly.allViews.filterIsInstance<ProxySettingsPage>().single()
                assertEquals(section, page.section)
                assertEquals(section.title, assembly.subpageTitle)
                assertEquals(screenBottom(activity.window.decorView), screenBottom(page))
                val bottom = (page.getChildAt(0).height - page.height + page.paddingTop + page.paddingBottom)
                    .coerceAtLeast(0)
                page.scrollTo(0, bottom)
                assertFalse(page.canScrollVertically(1))
                val insets = ViewCompat.getRootWindowInsets(page)!!
                val safeBottom = maxOf(
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.systemGestures()).bottom,
                    insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures()).bottom,
                )
                val lastControlBottom = page.allViews.filter { it !== page && it.isShown && it.isClickable }
                    .maxOf(::screenBottom)
                assertTrue("${section.name} 的最后控件须完整位于手势安全区上方",
                    lastControlBottom <= screenBottom(activity.window.decorView) - safeBottom)
            }
            // 宽屏保留中栏，直接切换下一三级页；手机先返回二级。
            var secondaryAttached = false
            main { secondaryAttached = entry.isAttachedToWindow }
            if (!secondaryAttached || section == ProxySettingsSection.entries.last()) {
                backToSection(assembly)
            }
        }
        main { assertFalse(assembly.closeSubpage()) }
        main { dispatcher.onBackPressed() }
        awaitUi { assembly.allViews.none { it is GroupedSwitchRow } }
    }

    @Test fun predictiveBackCanCancelThenFinishWithoutSkippingTheSection() {
        val assembly = openSyncSettings()
        openProxy(assembly, ProxySettingsSection.PORTS)
        lateinit var page: ProxySettingsPage
        main {
            page = assembly.allViews.filterIsInstance<ProxySettingsPage>().single()
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(.6f))
            assertEquals(ProxySettingsSection.PORTS.title, assembly.subpageTitle)
            dispatcher.dispatchOnBackCancelled()
        }
        awaitUi { settled(page) }
        main {
            assertSame(page, assembly.allViews.filterIsInstance<ProxySettingsPage>().single())
            assertEquals(ProxySettingsSection.PORTS.title, assembly.subpageTitle)
            dispatcher.dispatchOnBackStarted(backEvent(0f))
            dispatcher.dispatchOnBackProgressed(backEvent(.6f))
            dispatcher.onBackPressed()
        }
        awaitSecondary(assembly)
    }

    private val dispatcher get() = (activity as MainActivity).onBackPressedDispatcher

    private fun backEvent(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_LEFT)

    private fun openSyncSettings(): SettingsAssembly {
        main { host.visibility = View.GONE }
        val menuLabel = activity.getString(R.string.title_open_navigation_menu)
        val settingsLabel = activity.getString(R.string.nav_settings)
        if (clickVisibleAction(menuLabel, settingsLabel) == menuLabel) clickVisibleAction(settingsLabel)
        clickVisibleAction(activity.getString(R.string.settings_tile_sync))
        lateinit var assembly: SettingsAssembly
        awaitUi {
            assembly = activity.window.decorView.allViews.filterIsInstance<SettingsAssembly>().single()
            assembly.allViews.filterIsInstance<GroupedSwitchRow>().any {
                it.contentDescription == activity.getString(R.string.settings_sync_logs_save) && settled(it)
            }
        }
        return assembly
    }

    private fun openProxy(assembly: SettingsAssembly, section: ProxySettingsSection): SettingsButton {
        lateinit var entry: SettingsButton
        main {
            entry = assembly.allViews.filterIsInstance<SettingsButton>().single {
                it.contentDescription == activity.getString(section.titleRes)
            }
            entry.requestRectangleOnScreen(Rect(0, 0, entry.width, entry.height), true)
        }
        awaitUi { settled(entry) && entry.getGlobalVisibleRect(Rect()) }
        main { entry.performClick() }
        awaitUi {
            assembly.subpageTitle == section.title &&
                assembly.allViews.filterIsInstance<ProxySettingsPage>().any { it.section == section && settled(it) }
        }
        return entry
    }

    private fun backToSection(assembly: SettingsAssembly) {
        main { dispatcher.onBackPressed() }
        awaitSecondary(assembly)
    }

    private fun awaitSecondary(assembly: SettingsAssembly) = awaitUi {
        assembly.subpageTitle == null && assembly.allViews.none { it is ProxySettingsPage } &&
            assembly.allViews.filterIsInstance<GroupedSwitchRow>().any {
                it.contentDescription == activity.getString(R.string.settings_sync_logs_save) && settled(it)
            }
    }

    private fun clickVisibleAction(vararg labels: String): String {
        lateinit var target: View
        awaitUi {
            target = activity.window.decorView.allViews.firstOrNull { view ->
                labels.any { view.contentDescription == it } && view.isClickable && view.isEnabled &&
                    settled(view) && view.getGlobalVisibleRect(Rect())
            } ?: return@awaitUi false
            true
        }
        var label = ""
        main { label = target.contentDescription.toString(); target.performClick() }
        return label
    }

    private fun settled(view: View): Boolean = view.isShown && view.isLaidOut &&
        generateSequence(view) { it.parent as? View }.all {
            it.alpha == 1f && it.translationX == 0f && it.translationY == 0f && it.scaleX == 1f && it.scaleY == 1f
        }

    private fun screenBottom(view: View): Int = IntArray(2).let {
        view.getLocationOnScreen(it)
        it[1] + view.height
    }

    private fun awaitUi(condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 4000
        do {
            var ready = false
            main { ready = condition() }
            if (ready) return
            SystemClock.sleep(16)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("代理三级页未收敛到待验证的显示状态")
    }
}
