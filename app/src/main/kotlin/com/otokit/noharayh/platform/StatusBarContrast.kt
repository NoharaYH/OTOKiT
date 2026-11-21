package com.otokit.noharayh.platform

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import android.view.PixelCopy
import android.view.View
import android.view.ViewTreeObserver
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 取应用窗口在时钟和系统图标后面的实际画面，包括主题和设置页渐变。 */
internal class StatusBarContrast(private val window: Window) : ViewTreeObserver.OnDrawListener {
    private val root = window.decorView
    private val bars = WindowInsetsControllerCompat(window, root)
    private val sample = Bitmap.createBitmap(16, 3, Bitmap.Config.ARGB_8888)
    private var active = false
    private var scheduled = false
    private var copying = false
    private var nextSampleAt = 0L
    private val readFrame = Runnable {
        scheduled = false
        if (copying) scheduleSample() else copyStatusBar()
    }

    fun start() {
        if (active) return
        active = true
        root.viewTreeObserver.addOnDrawListener(this)
        root.invalidate()
    }

    fun stop() {
        if (!active) return
        active = false
        root.viewTreeObserver.removeOnDrawListener(this)
        root.removeCallbacks(readFrame)
        scheduled = false
    }

    override fun onDraw() = scheduleSample()

    private fun scheduleSample() {
        if (!active || scheduled) return
        scheduled = true
        root.postDelayed(readFrame, (nextSampleAt - SystemClock.uptimeMillis()).coerceAtLeast(16L))
    }

    private fun copyStatusBar() {
        if (!root.isAttachedToWindow || root.windowVisibility != View.VISIBLE || root.width == 0) return
        val height = ViewCompat.getRootWindowInsets(root)?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: return
        if (height < 3) return
        val strip = Rect(0, height / 3, root.width, height - height / 3)
        nextSampleAt = SystemClock.uptimeMillis() + 200L
        copying = true
        try {
            PixelCopy.request(window, strip, sample, { result ->
                copying = false
                if (active && result == PixelCopy.SUCCESS) updateIcons()
            }, root.handler)
        } catch (_: IllegalArgumentException) {
            // 切后台时窗口的 Surface 可能已在绘制回调后释放。
            copying = false
        }
    }

    private fun updateIcons() {
        var sum = 0.0
        for (y in 0 until sample.height) {
            for (x in 0 until sample.width / 4) {
                sum += Color.luminance(sample.getPixel(x, y))
                sum += Color.luminance(sample.getPixel(sample.width - 1 - x, y))
            }
        }
        val luminance = sum / (sample.height * (sample.width / 4) * 2)
        val dark = bars.isAppearanceLightStatusBars
        // 黑白等对比度的相对亮度约为 0.18，在附近留余量避免动态图案反复翻色。
        if (dark && luminance < .15) bars.isAppearanceLightStatusBars = false
        if (!dark && luminance > .21) bars.isAppearanceLightStatusBars = true
    }
}
