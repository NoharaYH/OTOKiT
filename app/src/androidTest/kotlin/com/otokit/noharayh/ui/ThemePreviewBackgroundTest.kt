package com.otokit.noharayh.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.otokit.noharayh.ui.kit.settings.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ThemePreviewBackgroundTest {
    @Test fun backgroundCropCoversTheRowFromRightAlignedStartToLeftAlignedFinish() {
        val source = Rect()
        val destination = RectF()
        for (width in listOf(300f, 882f, 1446f, 1916f, 3000f)) {
            val bounds = RectF(23f, 17f, 23f + width, 115f)
            for (step in 0..20) {
                themePreviewBackgroundBounds(1440, 160, bounds, 98f, step / 20f, source, destination)
                assertTrue(destination.left <= bounds.left + 0.001f)
                assertTrue(destination.right >= bounds.right - 0.001f)
                assertEquals(bounds.top, destination.top, 0.001f)
                assertEquals(bounds.bottom, destination.bottom, 0.001f)
                assertEquals(width + themePreviewTravel(width, 98f), destination.width(), 0.001f)
                assertEquals(720f, source.exactCenterX(), 0.5f)
                assertEquals(80f, source.exactCenterY(), 0.5f)
                assertTrue(source.left >= 0 && source.top >= 0 && source.right <= 1440 && source.bottom <= 160)
                if (step == 0) assertEquals(bounds.right, destination.right, 0.001f)
                if (step == 20) assertEquals(bounds.left, destination.left, 0.001f)
            }
        }
    }

    @Test fun backgroundOnlyEntranceNeverExposesTheRightEdge() {
        val background = Bitmap.createBitmap(1440, 160, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        for (width in listOf(882, 1916)) {
            val preview = ThemeMenuPreview(Color.RED, 1f)
            preview.images = ThemePreviewBitmaps(background, null, null)
            preview.setTextExtent(80f, width.toFloat())
            preview.render(1f, false)
            val bounds = RectF(0f, 0f, width.toFloat(), 44f)
            val path = Path().apply { addRect(bounds, Path.Direction.CW) }
            val frame = Bitmap.createBitmap(width, 44, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(frame)
            for (progress in listOf(0.1f, 0.25f, 0.5f, 0.75f, 1f)) {
                frame.eraseColor(Color.WHITE)
                preview.motion(progress)
                preview.draw(canvas, bounds, path)
                for (y in 1 until 43) {
                    val pixel = frame.getPixel(width - 1, y)
                    assertTrue("Right edge lost background at width=$width progress=$progress", Color.red(pixel) < 255)
                    assertEquals(255, Color.blue(pixel))
                }
            }
            frame.recycle()
        }
        background.recycle()
    }
}
