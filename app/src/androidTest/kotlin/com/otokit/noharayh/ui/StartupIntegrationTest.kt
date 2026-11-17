package com.otokit.noharayh.ui

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import android.widget.FrameLayout
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.MainActivity
import com.otokit.noharayh.R
import com.otokit.noharayh.storage.preferences.SharedPreferencesUiPreferences
import com.otokit.noharayh.ui.constants.dimensions.StartupDimensions
import com.otokit.noharayh.ui.kit.startup.StartupView
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class StartupIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test fun xmlSceneLoadsAndWideViewportKeepsPhoneDecorationCluster() {
        lateinit var view: StartupView
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync {
            view = StartupView(context)
            view.prepare { ready.countDown() }
        }
        assertTrue("Vector scene parsed before timeout", ready.await(3, TimeUnit.SECONDS))
        assertTrue(view.loaded)
        assertEquals(1100L, view.durationMs)
        val density = context.resources.displayMetrics.density
        fun render(widthDp: Int, time: Float): Bitmap {
            val result = Bitmap.createBitmap((widthDp*density).toInt(), (956*density).toInt(), Bitmap.Config.ARGB_8888)
            instrumentation.runOnMainSync {
                view.layout(0, 0, result.width, result.height)
                view.timeMs = time
                view.draw(Canvas(result))
            }
            return result
        }
        val phone = render(440, 1100f)
        val wide = render(1000, 1100f)
        val offset = (wide.width-phone.width)/2
        // Same bottom-centred artwork, including the curved outlines, at the same absolute size.
        var mismatches = 0
        for (y in (750*density).toInt() until phone.height step 5) {
            for (x in 0 until phone.width step 5) {
                val a = phone.getPixel(x,y)
                val b = wide.getPixel(x+offset,y)
                if (kotlin.math.abs(Color.red(a)-Color.red(b)) > 10) mismatches++
            }
        }
        assertTrue("Wide viewport must translate, not spread/stretch decoration: $mismatches", mismatches < 150)
        assertEquals(StartupDimensions.BACKGROUND, wide.getPixel(20, wide.height-80))
        val directory = File(context.getExternalFilesDir(null), "startup-validation").apply { mkdirs() }
        for (time in listOf(50f, 180f, 283f, 483f, 500f, 604f, 660f, 800f, 1100f)) {
            val bitmap = render(1000, time)
            File(directory, "frame-${time.toInt()}.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
        phone.recycle(); wide.recycle()
        instrumentation.runOnMainSync { view.dispose() }
    }

    @Test fun enabledAndDisabledLaunchBothReleaseOverlayAndRestoreAccessibility() {
        val preferences = SharedPreferencesUiPreferences(context)
        val original = preferences.readSelection().startupAnimationEnabled
        try {
            for (enabled in listOf(true, false)) {
                preferences.writeStartupAnimation(enabled)
                val activity = instrumentation.startActivitySync(Intent(context, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                SystemClock.sleep(1800)
                instrumentation.runOnMainSync {
                    val root = activity.findViewById<FrameLayout>(R.id.main)
                    assertNull("No startup input blocker remains", root.findViewWithTag<View>("startup_animation"))
                    assertNotEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, root.getChildAt(0).importantForAccessibility)
                    activity.finish()
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            val editor = context.getSharedPreferences(SharedPreferencesUiPreferences.FILE_NAME, 0).edit()
            if (original == null) editor.remove(SharedPreferencesUiPreferences.KEY_STARTUP_ANIMATION)
            else editor.putBoolean(SharedPreferencesUiPreferences.KEY_STARTUP_ANIMATION, original)
            editor.commit()
        }
    }
}
