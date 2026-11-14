package com.otokit.noharayh.ui

import android.graphics.Path
import android.graphics.RectF
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.otokit.noharayh.ui.animation.common.ContinuousCorners
import com.otokit.noharayh.ui.animation.common.ContinuousCapsulePath
import com.otokit.noharayh.ui.shell.background.AssetBackgroundLoader
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class ContinuousCornersTest : UiComponentTestHost() {
    @Test fun continuousCapsuleFitsOffsetPortraitLandscapeAndPressedBounds() {
        val shape = ContinuousCapsulePath()
        val path = Path()
        val actual = RectF()
        for ((width, height) in listOf(300f to 88f, 88f to 300f, 88f to 88f, 88.01f to 88f)) {
            for (inset in listOf(-8f, 0f, 3f, 6f)) {
                val bounds = RectF(13f + inset, 17f + inset, 13f + width - inset, 17f + height - inset)
                shape.setPath(path, bounds)
                // computeBounds includes conic control points outside the circular arc.
                // Measure the rendered contour instead of that control-point hull.
                val samples = path.approximate(0.001f)
                val xs = (1 until samples.size step 3).map { samples[it] }
                val ys = (2 until samples.size step 3).map { samples[it] }
                actual.set(xs.min(), ys.min(), xs.max(), ys.max())
                assertEquals(bounds.left, actual.left, 0.001f)
                assertEquals(bounds.top, actual.top, 0.001f)
                assertEquals(bounds.right, actual.right, 0.001f)
                assertEquals(bounds.bottom, actual.bottom, 0.001f)
                assertTrue(path.approximate(0.05f).all { it.isFinite() })
                assertTrue(android.graphics.PathMeasure(path, false).isClosed)
            }
        }
    }

    @Test fun dropdownCornersMatchCapsuleAndStayBoundedDuringExpansion() {
        val rounded = com.otokit.noharayh.ui.animation.common.ContinuousRoundedPath()
        val capsule = ContinuousCapsulePath()
        val path = Path()
        val expected = Path()
        for (extra in listOf(0f, 0.01f, 1f, 12f, 44f, 300f)) {
            val bounds = RectF(13f, 17f, 313f, 105f + extra)
            rounded.setPath(path, bounds, 44f, 44f - extra.coerceAtMost(28f))
            assertTrue(android.graphics.PathMeasure(path, false).isClosed)
            val samples = path.approximate(0.001f)
            for (i in samples.indices step 3) {
                assertTrue(samples[i + 1] in (bounds.left - 0.001f)..(bounds.right + 0.001f))
                assertTrue(samples[i + 2] in (bounds.top - 0.001f)..(bounds.bottom + 0.001f))
            }
            if (extra == 0f) {
                capsule.setPath(expected, bounds)
                // Splitting circular conics at the tip changes pixel rounding in Region.
                // Compare contour positions rather than integer raster coverage.
                val actualMeasure = android.graphics.PathMeasure(path, false)
                val expectedMeasure = android.graphics.PathMeasure(expected, false)
                val actualPoint = FloatArray(2)
                val expectedPoint = FloatArray(2)
                assertEquals(expectedMeasure.length, actualMeasure.length, 0.05f)
                for (step in 0..200) {
                    actualMeasure.getPosTan(actualMeasure.length * step / 200, actualPoint, null)
                    expectedMeasure.getPosTan(expectedMeasure.length * step / 200, expectedPoint, null)
                    assertEquals(expectedPoint[0], actualPoint[0], 0.05f)
                    assertEquals(expectedPoint[1], actualPoint[1], 0.05f)
                }
            }
        }
    }

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
