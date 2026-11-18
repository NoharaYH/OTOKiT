package com.otokit.noharayh.preferences

import com.otokit.noharayh.score.HtmlPageKind
import com.otokit.noharayh.score.ScoreGame
import com.otokit.noharayh.transfer.CaptureRequest
import org.junit.Assert.*
import org.junit.Test

class CapturePreferencesTest {
    @Test fun missingValueIsOffAndChangesOnlyAffectNewRequests() {
        val stored = mutableMapOf<String, Boolean>()
        val preferences = CapturePreferences(stored::get) { key, value -> stored[key] = value }
        assertFalse(preferences.keepCallbackCaptureUntilLogin())
        preferences.setKeepCallbackCaptureUntilLogin(true)
        val request = CaptureRequest(ScoreGame.MAIMAI, listOf(HtmlPageKind.MASTER), "one",
            preferences.keepCallbackCaptureUntilLogin())
        preferences.setKeepCallbackCaptureUntilLogin(false)
        assertTrue(request.keepCallbackCaptureUntilLogin)
        assertFalse(preferences.keepCallbackCaptureUntilLogin())
    }
}
