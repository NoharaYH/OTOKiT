package com.otokit.noharayh.ui.animation.chrome

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.otokit.noharayh.ui.animation.common.SettingsPageFrames
import com.otokit.noharayh.ui.animation.common.TransitionProgress

/** 父子页标题只呈现设置时钟的进度，拥有一份裁剪到文字范围的退场截图。 */
internal class SettingsPageTitleTransitionRunner(
    private val host: FrameLayout,
    private val column: View,
    private val bounds: () -> RectF?,
    private val refreshPivot: () -> Unit,
) {
    private var ghost: ImageView? = null
    private var bitmap: Bitmap? = null
    private val baselineY = column.translationY
    private var forward = true
    private var split = false
    private var distanceWidth = 0f
    private var restore: (() -> Unit)? = null
    var active = false
        private set

    fun prepare(
        forward: Boolean,
        split: Boolean,
        distanceWidth: Float,
        restoreSource: () -> Unit,
        showTarget: () -> Unit,
    ) {
        settle()
        this.forward = forward
        this.split = split
        this.distanceWidth = distanceWidth
        restore = restoreSource
        val rect = bounds()
        if (rect != null && rect.width() > 0 && rect.height() > 0) {
            val left = kotlin.math.floor(rect.left).toInt()
            val top = kotlin.math.floor(rect.top).toInt()
            val w = kotlin.math.ceil(rect.right).toInt() - left
            val h = kotlin.math.ceil(rect.bottom).toInt() - top
            var captured: Bitmap? = null
            try {
                captured = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(captured)
                canvas.translate(-left.toFloat(), -top.toFloat())
                column.draw(canvas)
            } catch (_: OutOfMemoryError) {
                captured?.recycle()
                captured = null
            } catch (_: RuntimeException) {
                captured?.recycle()
                captured = null
            }
            if (captured != null) {
                bitmap = captured
                ghost = ImageView(host.context).apply {
                    setImageBitmap(captured)
                    importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                    isClickable = false
                    isFocusable = false
                    pivotX = rect.left - left
                    pivotY = rect.centerY() - top
                    host.addView(this, FrameLayout.LayoutParams(w, h).apply {
                        leftMargin = column.left + left
                        topMargin = column.top + top
                    })
                }
            }
        }
        showTarget()
        active = true
        render(TransitionProgress(0f, true))
    }

    fun render(frame: TransitionProgress) {
        if (!active) return
        refreshPivot()
        val width = distanceWidth.takeIf { it > 0f } ?: host.width.toFloat()
        val entry = SettingsPageFrames.title(frame.value, true, forward, split, width)
        column.alpha = entry.alpha
        column.scaleX = entry.scale
        column.scaleY = entry.scale
        column.translationX = entry.translationX
        column.translationY = baselineY + entry.translationY
        ghost?.let {
            val exit = SettingsPageFrames.title(frame.value, false, forward, split, width)
            it.alpha = exit.alpha
            it.scaleX = exit.scale
            it.scaleY = exit.scale
            it.translationX = exit.translationX
            it.translationY = baselineY + exit.translationY
        }
        if (!frame.running) {
            if (frame.value <= 0f) restore?.invoke()
            settle()
        }
    }

    fun settle() {
        active = false
        restore = null
        ghost?.let { it.setImageDrawable(null); host.removeView(it) }
        ghost = null
        bitmap?.recycle()
        bitmap = null
        column.alpha = 1f
        column.scaleX = 1f
        column.scaleY = 1f
        column.translationX = 0f
        column.translationY = baselineY
    }
}
