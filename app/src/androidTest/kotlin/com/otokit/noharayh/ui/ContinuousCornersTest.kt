package com.otokit.noharayh.ui

import android.graphics.Path
import android.graphics.RectF
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otokit.noharayh.ui.animation.common.ContinuousCorners
import com.otokit.noharayh.ui.shell.background.AssetBackgroundLoader
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContinuousCornersTest : UiComponentTestHost() {
    @Test fun animatedAndUnequalCornersStayConvexAndInsideBounds() {
        val path = Path()
        val bounds = RectF()
        for (width in listOf(1f, 6f, 44f, 300f)) for (height in listOf(1f, 6f, 44f, 400f)) {
            val rect = RectF(7f, 11f, 7f + width, 11f + height)
            for (top in listOf(0f, 4f, 16f, 22f, 200f)) for (bottom in listOf(0f, 3f, 22f, 200f)) {
                ContinuousCorners.setPath(path, rect, top, bottom)
                @Suppress("DEPRECATION") // Keep this check usable on the minimum API 26.
                path.computeBounds(bounds, true)
                assertEquals(rect.left, bounds.left, 0.001f)
                assertEquals(rect.top, bounds.top, 0.001f)
                assertEquals(rect.right, bounds.right, 0.001f)
                assertEquals(rect.bottom, bounds.bottom, 0.001f)
                val points = path.approximate(0.01f)
                assertTrue(points.all { it.isFinite() })
                // Skia's isConvex can reject very thin paths due to float rounding.
                // Check the actual sampled contour, allowing subpixel numerical noise.
                for (i in 3 until points.size - 3 step 3) {
                    val ax = points[i + 1] - points[i - 2]
                    val ay = points[i + 2] - points[i - 1]
                    val bx = points[i + 4] - points[i + 1]
                    val by = points[i + 5] - points[i + 2]
                    assertTrue("Reversed turn $rect / $top / $bottom", ax * by - ay * bx >= -0.001f)
                }
            }
        }
    }

    @Test fun fullRadiusCapsuleKeepsCircularEnds() {
        val path = Path()
        ContinuousCorners.setPath(path, RectF(0f, 0f, 200f, 44f), 22f)
        val points = path.approximate(0.02f)
        for (i in points.indices step 3) {
            val x = points[i + 1]; val y = points[i + 2]
            if (x < 22f || x > 178f) {
                val centerX = if (x < 22f) 22f else 178f
                val distance = kotlin.math.hypot(x - centerX, y - 22f)
                assertEquals(22f, distance, 0.025f)
            }
        }
    }

    @Test fun threeOptedInBackgroundsClipAndRefreshAfterResize() {
        for ((theme, selector) in listOf("MaiCirclePlus" to ".maiDecorationBg .background-layer",
            "MaiPrismPlus" to ".maiDecorationBg .background-layer", "MaiBuddiesPlus" to ".dec-bg")) {
            lateinit var web: WebView
            val loaded = CountDownLatch(1)
            main {
                val loader = AssetBackgroundLoader(activity)
                web = WebView(activity).apply {
                    settings.javaScriptEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = loader.intercept(request)
                        override fun onPageFinished(view: WebView, url: String?) { loaded.countDown() }
                    }
                }
                loader.load(web, "background/$theme/html/index.html")
            }
            mount(web, 600, 700)
            try {
                assertTrue("Load $theme", loaded.await(15, TimeUnit.SECONDS))
                fun readClip(): String {
                    val done = CountDownLatch(1)
                    var value = ""
                    main {
                        web.evaluateJavascript("getComputedStyle(document.querySelector('$selector')).clipPath") {
                            value = it; done.countDown()
                        }
                    }
                    assertTrue(done.await(5, TimeUnit.SECONDS))
                    return value
                }
                val before = readClip()
                assertTrue("Missing clip for $theme: $before", before.contains("path"))
                main { web.layoutParams = web.layoutParams.apply { width = 420; height = 520 } }
                instrumentation.waitForIdleSync()
                val deadline = android.os.SystemClock.uptimeMillis() + 3000
                var after = readClip()
                while (after == before && android.os.SystemClock.uptimeMillis() < deadline) {
                    android.os.SystemClock.sleep(30)
                    after = readClip()
                }
                assertTrue(after.contains("path"))
                assertNotEquals("Stale clip after resizing $theme", before, after)
            } finally {
                main { host.removeView(web); web.destroy() }
            }
        }
    }
}
