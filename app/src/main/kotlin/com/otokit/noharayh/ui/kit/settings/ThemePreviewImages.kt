package com.otokit.noharayh.ui.kit.settings

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.graphics.scale
import androidx.core.graphics.createBitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import kotlin.math.min
import com.otokit.noharayh.ui.theme.ThemePreviewDescription
import com.otokit.noharayh.ui.theme.ThemePreviewCrop
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadPoolExecutor
import kotlin.math.max
import kotlin.math.roundToInt

internal data class ThemePreviewBitmaps(val background: Bitmap, val decoration: Bitmap, val eyes: Bitmap?, val flat: Bitmap,
    val decorationCrop: ThemePreviewCrop? = null) {
    val bytes: Int get() = background.allocationByteCount + decoration.allocationByteCount + (eyes?.allocationByteCount ?: 0) + flat.allocationByteCount
}

/** 菜单局部、有界缓存；完整一组解码后交接，过期代次绝不回写。 */
internal class ThemePreviewImages(
    private val assets: AssetManager,
    private val descriptions: List<ThemePreviewDescription?>,
    private val publish: (Int, ThemePreviewBitmaps?) -> Unit,
) {
    private val executor = Executors.newFixedThreadPool(1) as ThreadPoolExecutor
    private val handler = Handler(Looper.getMainLooper())
    private val cache = LinkedHashMap<Int, ThemePreviewBitmaps>(8, 0.75f, true)
    private val pending = HashMap<Int, Future<*>>()
    private val failed = HashSet<Int>()
    private val tickets = IntArray(descriptions.size)
    private var visible = emptySet<Int>()
    private var generation = 0
    private var width = 0
    private var height = 0
    private var active = false
    private var closed = false
    var cachedBytes = 0
        private set
    val hasPendingWork: Boolean get() = pending.isNotEmpty()

    fun setActive(value: Boolean) {
        if (active == value || closed) return
        active = value
        if (!active) cancelRequests()
        else update(visible, width, height)
    }

    fun update(indices: Set<Int>, targetWidth: Int, targetHeight: Int) {
        if (closed || targetWidth <= 0 || targetHeight <= 0) return
        if (width != targetWidth || height != targetHeight) {
            cancelRequests()
            cache.clear(); failed.clear(); cachedBytes = 0
            for (index in descriptions.indices) publish(index, null)
            width = targetWidth; height = targetHeight
        }
        for (index in visible - indices) publish(index, null)
        visible = indices
        // 不让快速长列表滚动积压后台任务；至多当前完整/交换中的行。
        for (index in pending.keys.toList()) if (index !in visible) pending.remove(index)?.cancel(true).also { tickets[index]++ }
        executor.purge()
        if (!active) return
        val currentGeneration = generation
        for (index in visible.sorted()) {
            val description = descriptions[index] ?: continue
            val cached = cache[index]
            if (cached != null) { publish(index, cached); continue }
            if (index in pending || index in failed) continue
            val ticket = ++tickets[index]
            pending[index] = executor.submit {
                val images = try {
                    val background = decode(description.background, targetWidth, targetHeight, cover = true)
                    val crop = description.decorationCrop
                    val decorationHeight = if (crop == null) targetHeight else
                        (targetHeight.toFloat() * crop.height / crop.canvasHeight).roundToInt().coerceAtLeast(1)
                    val decoration = decode(description.decoration, targetWidth, decorationHeight)
                    val eyes = description.eyes?.let { decode(it, targetWidth, targetHeight) }
                    val flat = compose(background, decoration, eyes, targetWidth, targetHeight, crop)
                    ThemePreviewBitmaps(background, decoration, eyes, flat, crop)
                } catch (error: Exception) {
                    if (!Thread.currentThread().isInterrupted) Log.w("ThemePreview", "Cannot decode ${description.assetFolder}", error)
                    null
                }
                if (Thread.currentThread().isInterrupted) return@submit
                handler.post {
                    if (closed || generation != currentGeneration || index !in pending || tickets[index] != ticket) return@post
                    pending.remove(index)
                    if (images == null) { failed.add(index); return@post }
                    cache.put(index, images)?.let { cachedBytes -= it.bytes }
                    cachedBytes += images.bytes
                    trim()
                    if (index in visible && active) publish(index, images)
                }
            }
        }
    }

    private fun decode(path: String, targetWidth: Int, targetHeight: Int, cover: Boolean = false): Bitmap {
        check(!Thread.currentThread().isInterrupted)
        val dimensions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, dimensions) }
        require(dimensions.outWidth > 0 && dimensions.outHeight > 0)
        // 背景覆盖实际宽度；不放大源文件，不按平板整屏分配超大 Bitmap。
        val fit = if (cover) max(targetWidth.toFloat() / dimensions.outWidth, targetHeight.toFloat() / dimensions.outHeight)
            else targetHeight.toFloat() / dimensions.outHeight
        val scale = min(min(Dimensions.PREVIEW_BITMAP_MAX_WIDTH_PX.toFloat() / dimensions.outWidth,
            Dimensions.PREVIEW_BITMAP_MAX_HEIGHT_PX.toFloat() / dimensions.outHeight),
            fit).coerceAtMost(1f)
        var sample = 1
        while (sample * 2 <= 1f / scale) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val decoded = assets.open(path).use { BitmapFactory.decodeStream(it, null, options) } ?: error("Invalid image: $path")
        val w = (dimensions.outWidth * scale).roundToInt().coerceAtLeast(1)
        val h = (dimensions.outHeight * scale).roundToInt().coerceAtLeast(1)
        return if (decoded.width == w && decoded.height == h) decoded else {
            decoded.scale(w, h, true).also { if (it !== decoded) decoded.recycle() }
        }
    }

    private fun compose(background: Bitmap, decoration: Bitmap, eyes: Bitmap?, width: Int, height: Int, crop: ThemePreviewCrop?): Bitmap {
        check(!Thread.currentThread().isInterrupted)
        val scale = min(1f, min(Dimensions.PREVIEW_BITMAP_MAX_WIDTH_PX.toFloat() / width,
            Dimensions.PREVIEW_BITMAP_MAX_HEIGHT_PX.toFloat() / height))
        val flat = createBitmap((width * scale).roundToInt().coerceAtLeast(1),
            (height * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(flat)
        canvas.scale(flat.width.toFloat() / width, flat.height.toFloat() / height)
        val bounds = RectF(0f, 0f, width.toFloat(), height.toFloat())
        val destination = RectF()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        fun layer(bitmap: Bitmap, cover: Boolean, layerCrop: ThemePreviewCrop? = null) {
            themePreviewLayerBounds(bitmap.width, bitmap.height, bounds, height.toFloat(), cover, destination, layerCrop)
            canvas.drawBitmap(bitmap, null, destination, paint)
        }
        layer(background, true)
        layer(decoration, false, layerCrop = crop)
        eyes?.let { layer(it, false) }
        return flat
    }

    private fun trim() {
        val iterator = cache.entries.iterator()
        while (cachedBytes > BYTE_BUDGET && iterator.hasNext()) {
            val entry = iterator.next()
            // 局部 UI 正在使用的帧不主动 recycle；移除缓存后由引用生命周期释放。
            if (entry.key !in visible) { cachedBytes -= entry.value.bytes; iterator.remove() }
        }
    }

    private fun cancelRequests() {
        generation++
        pending.values.forEach { it.cancel(true) }; pending.clear()
        executor.purge()
        handler.removeCallbacksAndMessages(null)
    }

    fun close() {
        if (closed) return
        closed = true; cancelRequests(); executor.shutdownNow()
        visible = emptySet(); cache.clear(); failed.clear(); cachedBytes = 0
        for (index in descriptions.indices) publish(index, null)
    }

    companion object { const val BYTE_BUDGET = Dimensions.PREVIEW_CACHE_BYTES }
}
