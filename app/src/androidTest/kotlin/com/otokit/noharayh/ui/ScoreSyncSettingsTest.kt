package com.otokit.noharayh.ui

import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.composition.capturePreferences
import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import com.otokit.noharayh.transfer.runtime.NetCaptureService
import com.otokit.noharayh.ui.kit.common.toggle.GroupedSwitchRow
import com.otokit.noharayh.ui.settings.subPages.ScoreSyncSettingsPage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScoreSyncSettingsTest : UiComponentTestHost() {
    @Test fun settingSurvivesPageRebuildAndLaunchIntentKeepsItsOriginalValue() {
        val preferences = capturePreferences(activity)
        val original = preferences.keepCallbackCaptureUntilLogin()
        try {
            preferences.setKeepCallbackCaptureUntilLogin(false)
            lateinit var page: View
            main { page = ScoreSyncSettingsPage.create(activity, 20, false) }
            mount(page, activity.resources.displayMetrics.widthPixels - 160, -1)
            main {
                val toggle = switchIn(page)!!
                assertFalse(toggle.isChecked)
                toggle.performClick()
                assertTrue(toggle.isChecked)
                assertTrue(capturePreferences(activity).keepCallbackCaptureUntilLogin())
                val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "settings-test",
                    preferences.keepCallbackCaptureUntilLogin())
                val launch = NetCaptureService.startIntent(activity, request)
                host.removeView(page)
                page = ScoreSyncSettingsPage.create(activity, 20, false)
                val restored = switchIn(page)!!
                assertTrue(restored.isChecked)
                restored.performClick()
                assertFalse(restored.isChecked)
                assertFalse(capturePreferences(activity).keepCallbackCaptureUntilLogin())
                assertTrue(launch.getBooleanExtra("keepCallbackCaptureUntilLogin", false))
            }
        } finally { preferences.setKeepCallbackCaptureUntilLogin(original) }
    }

    private fun switchIn(view: View): GroupedSwitchRow? {
        if (view is GroupedSwitchRow) return view
        if (view is ViewGroup) for (index in 0 until view.childCount) {
            switchIn(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
