package com.otokit.noharayh.ui.kit.startup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.otokit.noharayh.ui.constants.dimensions.StartupDimensions as D

/** 启动专属矢量组件。只消费时间，不读取设置、不创建 Animator、不导航。 */
@android.annotation.SuppressLint("ViewConstructor")
class StartupView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeJoin = Paint.Join.ROUND }
    private val density = resources.displayMetrics.density
    private var scene: StartupScene? = null
    private var loader: java.util.concurrent.ExecutorService? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private var disposed = false
    val durationMs: Long get() = scene?.durationMs ?: 0L
    val loaded: Boolean get() = scene != null
    var timeMs = 0f
        set(value) { field = value; invalidate() }

    init {
        setBackgroundColor(D.BACKGROUND)
        isClickable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        tag = "startup_animation"
    }

    /** 后台解析，加载结果只属于此 View；卸载后拒绝晚到回调。 */
    fun prepare(onReady: () -> Unit) {
        check(loader == null && !disposed)
        val resources = resources
        loader = java.util.concurrent.Executors.newSingleThreadExecutor().also { executor ->
            executor.execute {
                val result = runCatching { StartupScene.load(resources) }
                handler.post {
                    if (!disposed) {
                        scene = result.getOrNull()
                        result.exceptionOrNull()?.let { android.util.Log.e("StartupScene", "Cannot load startup vectors", it) }
                        invalidate()
                        onReady()
                    }
                }
                executor.shutdown()
            }
        }
    }

    fun dispose() {
        disposed = true
        loader?.shutdownNow()
        loader = null
        handler.removeCallbacksAndMessages(null)
        scene = null
    }

    override fun onDetachedFromWindow() { dispose(); super.onDetachedFromWindow() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val scene = scene ?: return
        canvas.save()
        canvas.scale(density, density)
        val w = width / density
        val h = height / density
        for (node in scene.nodes) {
            canvas.save()
            if (node.type == "decoration") {
                canvas.translate(w / 2f - D.CENTER_X, h - D.HEIGHT)
                val cx = node.attr("cx")
                val cy = node.attr("cy")
                canvas.translate(node.value("--motion-translate-x", timeMs), node.value("--motion-translate-y", timeMs))
                canvas.rotate(node.value("--motion-rotate", timeMs) * 180f / Math.PI.toFloat(), cx, cy)
                val scale = node.value("--motion-scale-x", timeMs, 1f)
                canvas.scale(scale, scale, cx, cy)
                drawInk(canvas, node)
            } else {
                canvas.translate(w / 2f - D.CENTER_X, h * D.LOGO_HEIGHT_FRACTION - D.CENTER_Y)
                canvas.translate(0f, node.value("--motion-translate-y", timeMs))
                when (node.type) {
                    "arrows" -> {
                        val alpha = node.value("opacity", timeMs, 1f)
                        if (alpha > 0f) node.children.firstOrNull { it.value("opacity", timeMs) > 0.5f }
                            ?.let { drawInk(canvas, it, alpha) }
                    }
                    "ring" -> {
                        val scale = node.value("--motion-scale-x", timeMs, 1f)
                        canvas.scale(scale, scale, D.CENTER_X, D.CENTER_Y)
                        paint.style = Paint.Style.STROKE
                        paint.strokeWidth = node.value("border-width", timeMs)
                        paint.color = node.tracks.getValue("border-color").value(timeMs).toInt()
                        paint.alpha = (255 * node.value("opacity", timeMs)).toInt().coerceIn(0, 255)
                        canvas.drawCircle(D.CENTER_X, D.CENTER_Y, node.attr("radius"), paint)
                    }
                    "kit" -> {
                        val scale = node.value("--motion-scale-x", timeMs, 1f)
                        canvas.scale(scale, scale, D.CENTER_X, D.CENTER_Y)
                        canvas.translate(node.attr("x"), node.attr("y"))
                        drawInk(canvas, node, node.value("opacity", timeMs))
                    }
                }
            }
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawInk(canvas: Canvas, node: StartupScene.Node, opacity: Float = 1f) {
        if (opacity <= 0f) return
        for (ink in node.paths) {
            paint.color = ink.color
            paint.alpha = (255 * opacity).toInt().coerceIn(0, 255)
            paint.style = if (ink.stroke > 0) Paint.Style.STROKE else Paint.Style.FILL
            paint.strokeWidth = ink.stroke
            canvas.drawPath(ink.path, paint)
        }
    }
}
