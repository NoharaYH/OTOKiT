package com.otokit.noharayh.ui.shell

import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/** 只读取系统公开的壁纸色，不获取位图或申请存储权限。监听与 shell 同生共灭。 */
internal class WallpaperColorSource(context: Context, private val onColor: (Int) -> Unit) {
    private val manager = WallpaperManager.getInstance(context.applicationContext)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private var closed = false
    private var generation = 0
    private val listener = if (Build.VERSION.SDK_INT >= 27) WallpaperManager.OnColorsChangedListener { colors, which ->
        if (!closed && which and WallpaperManager.FLAG_SYSTEM != 0) {
            generation++
            colors?.primaryColor?.toArgb()?.let(onColor)
        }
    } else null

    fun start() {
        if (Build.VERSION.SDK_INT >= 27) {
            listener?.let { manager.addOnColorsChangedListener(it, main) }
            refresh()
        }
    }

    fun refresh() {
        if (closed || Build.VERSION.SDK_INT < 27) return
        val request = ++generation
        worker.execute {
            val color = try {
                manager.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)?.primaryColor?.toArgb()
            } catch (_: RuntimeException) { null } // 厂商不提供壁纸色时保留上次的合法种子。
            main.post { if (!closed && request == generation && color != null) onColor(color) }
        }
    }

    fun close() {
        closed = true
        generation++
        if (Build.VERSION.SDK_INT >= 27) listener?.let(manager::removeOnColorsChangedListener)
        worker.shutdownNow()
    }
}
