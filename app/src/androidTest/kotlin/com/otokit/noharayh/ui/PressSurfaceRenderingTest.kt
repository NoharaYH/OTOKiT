package com.otokit.noharayh.ui

import android.graphics.Canvas
import android.graphics.Color
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
