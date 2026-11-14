package com.otokit.noharayh.ui.shell.background

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import com.otokit.noharayh.ui.UiComponentTestHost
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class MistBackgroundRenderingTest : UiComponentTestHost() {
    @Test fun renderMasterAndCropsAndPausePlayback() {
        val context = instrumentation.targetContext
        lateinit var drawable: MistBackgroundDrawable
        val frames = AtomicInteger()
        instrumentation.runOnMainSync {
            drawable = MistBackgroundDrawable(context, dynamicColors = false)
            drawable.callback = object : Drawable.Callback {
                override fun invalidateDrawable(who: Drawable) { frames.incrementAndGet() }
                override fun scheduleDrawable(who: Drawable, what: Runnable, `when`: Long) = Unit
                override fun unscheduleDrawable(who: Drawable, what: Runnable) = Unit
            }
            for ((name, size) in listOf("master" to (1600 to 1200), "phone" to (480 to 1080), "wide" to (1600 to 800))) {
                val bitmap = Bitmap.createBitmap(size.first, size.second, Bitmap.Config.ARGB_8888)
                drawable.setBounds(0,0,size.first,size.second)
                drawable.draw(Canvas(bitmap))
                assertEquals(255, Color.alpha(bitmap.getPixel(0,0)))
                assertEquals(255, Color.alpha(bitmap.getPixel(size.first-1,size.second-1)))
                File(context.getExternalFilesDir(null), "mist-$name.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG,100,it)
                }
                bitmap.recycle()
            }
            drawable.start()
            assertTrue(drawable.isRunning)
        }
        Thread.sleep(24_500)
        instrumentation.runOnMainSync { drawable.stop(); assertFalse(drawable.isRunning) }
        assertTrue("Timeline must produce frames through one full loop", frames.get() > 100)
        val paused = frames.get()
        Thread.sleep(150)
        assertEquals("Hidden background must stop invalidating", paused, frames.get())
        instrumentation.runOnMainSync {
            drawable.start(); assertTrue(drawable.isRunning)
            drawable.setVisible(false, false); assertFalse(drawable.isRunning)
            drawable.release()
        }
    }
}
