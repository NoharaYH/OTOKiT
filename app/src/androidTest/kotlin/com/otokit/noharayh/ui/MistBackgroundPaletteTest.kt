package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.app.WallpaperManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.otokit.noharayh.ui.shell.background.MistBackgroundDrawable
import com.otokit.noharayh.ui.shell.MaterkitColors
import com.otokit.noharayh.ui.theme.NoneThemePalette
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MistBackgroundPaletteTest {
    @Test fun materkitUsesWallpaperEvenWhenSystemAccentIsDifferent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val green = MaterkitColors.resolve(context, 0xFF006E24.toInt())
        val pink = MaterkitColors.resolve(context, 0xFFB00068.toInt())
        assertNotEquals(green, pink)
        assertEquals(NoneThemePalette.fromSeed(0xFF006E24.toInt()).colors, green)
        val wallpaper = WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        assertNotNull("Device must expose wallpaper colors", wallpaper)
        val seed = wallpaper!!.primaryColor.toArgb()
        assertEquals(NoneThemePalette.fromSeed(seed).colors, MaterkitColors.resolve(context, seed))
    }

    @Test fun recoloringExistingDrawableMatchesFreshPaletteAndCanRestoreOriginal() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val drawable = MistBackgroundDrawable(instrumentation.targetContext)
            val fresh = MistBackgroundDrawable(instrumentation.targetContext)
            fun render(value: MistBackgroundDrawable): Bitmap {
                value.setBounds(0, 0, 160, 120)
                return Bitmap.createBitmap(160, 120, Bitmap.Config.ARGB_8888).also {
                    value.draw(Canvas(it))
                }
            }
            try {
                drawable.setPrimaryColor(0xFF006E24.toInt())
                val green = render(drawable)
                drawable.setPrimaryColor(0xFFB00068.toInt())
                val pink = render(drawable)
                assertFalse("Existing background must change color", green.sameAs(pink))
                fresh.setPrimaryColor(0xFFB00068.toInt())
                assertTrue("Recolor from source colors, without cumulative color loss", pink.sameAs(render(fresh)))
                drawable.setPrimaryColor(0xFF006E24.toInt())
                assertTrue("Switching back must restore the original palette", green.sameAs(render(drawable)))
                assertFalse("Color updates must not start a stopped animation", drawable.isRunning)
            } finally {
                drawable.release()
                fresh.release()
            }
        }
    }
}
