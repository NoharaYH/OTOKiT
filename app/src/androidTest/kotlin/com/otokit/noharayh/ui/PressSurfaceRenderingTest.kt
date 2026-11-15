package com.otokit.noharayh.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Bitmap
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.otokit.noharayh.ui.kit.common.PressSurface
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class PressSurfaceRenderingTest : UiComponentTestHost() {
    @Test fun surfaceContentIsClippedToActualPressShapeAndStaysBelowFeedback() {
        lateinit var surface: PressSurface
        var lastInset = 0f
        main {
            surface = PressSurface(activity).apply {
                fillColor = Color.BLUE
                baseRadius = 40f
                surfaceContent = { canvas, bounds, _ ->
                    lastInset = bounds.left
                    // 故意画满 Canvas，验证裁剪由表面负责而非装饰自行假定圆角。
                    canvas.drawColor(Color.RED)
                }
            }
        }
        mount(surface, 260, 160)
        fun pixels(): Pair<Int, Int> {
            val bitmap = Bitmap.createBitmap(surface.width, surface.height, Bitmap.Config.ARGB_8888)
            return try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                surface.draw(canvas)
                bitmap.getPixel(1, 1) to bitmap.getPixel(surface.width / 2, surface.height / 2)
            } finally { bitmap.recycle() }
        }
        main { assertEquals(Color.WHITE to Color.RED, pixels()) }
        touch(surface, MotionEvent.ACTION_DOWN, Triple(0, 130f, 80f))
        android.os.SystemClock.sleep(200)
        main {
            val (corner, center) = pixels()
            assertEquals(Color.WHITE, corner)
            assertTrue(lastInset > 0f)
            assertTrue("按压反馈必须在装饰之上", center != Color.RED)
        }
        touch(surface, MotionEvent.ACTION_CANCEL, Triple(0, 130f, 80f))
    }

    @Test fun changingGeometryRedrawsTheAttachedSurfaceWithoutCallerInvalidation() {
        lateinit var surface: PressSurface
        var drawn: CountDownLatch? = null
        main {
            surface = object : PressSurface(activity) {
                override fun onDraw(canvas: Canvas) {
                    super.onDraw(canvas)
                    drawn?.countDown()
                }
            }.apply { fillColor = Color.RED; baseRadius = 0f }
        }
        mount(surface, 200, 120)
        val initialFrame = CountDownLatch(1)
        main {
            surface.viewTreeObserver.registerFrameCommitCallback { initialFrame.countDown() }
            surface.invalidate()
        }
        assertTrue(initialFrame.await(2, TimeUnit.SECONDS))
        val position = IntArray(2)
        main { surface.getLocationOnScreen(position) }
        fun corner(): Int {
            val bitmap = requireNotNull(instrumentation.uiAutomation.takeScreenshot())
            return bitmap.getPixel(position[0] + 3, position[1] + 3).also { bitmap.recycle() }
        }
        assertEquals(Color.RED, corner())
        val changes: List<Pair<String, () -> Unit>> = listOf(
            "baseRadius" to { surface.baseRadius = 50f },
            "topCornerRadius" to { surface.topCornerRadius = 30f },
            "bottomCornerRadius" to { surface.bottomCornerRadius = 20f },
            "pressInset" to { surface.pressInset = 4f },
            "bottomCornerPressFraction" to { surface.bottomCornerPressFraction = 0.5f },
            "innerRippleEnabled" to { surface.innerRippleEnabled = false },
            "whiteMaxAlpha" to { surface.whiteMaxAlpha = 0.2f },
            "followTauMs" to { surface.followTauMs = 40f },
            "rippleRestDiameter" to { surface.rippleRestDiameter = 80f },
        )
        for ((name, change) in changes) {
            val frame = CountDownLatch(1)
            val committed = CountDownLatch(1)
            main {
                drawn = frame
                surface.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                change()
            }
            assertTrue("$name 修改后应提交新绘制帧", frame.await(2, TimeUnit.SECONDS))
            assertTrue("$name 修改后的帧应提交到 GPU", committed.await(2, TimeUnit.SECONDS))
            instrumentation.waitForIdleSync()
        }
        // 取系统截图证明圆角已实际显示，不只是属性值发生变化。
        assertEquals(Color.WHITE, corner())
        main {
            assertEquals(200, surface.width)
            assertEquals(120, surface.height)
        }
    }
}
