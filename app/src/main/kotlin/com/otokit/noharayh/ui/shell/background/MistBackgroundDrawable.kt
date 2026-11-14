package com.otokit.noharayh.ui.shell.background

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.util.Xml
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.otokit.noharayh.ui.shell.MaterkitColors
import com.materialkolor.hct.Hct
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max
import kotlin.math.min
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Figma WTnoHx4OnRUwv0svJ2FjV0 / 12:224. Fixed 1600×1200 master, centered cover crop.
 * Original SVG ellipses are parsed once; no WebView, bitmap textures or per-frame parsing.
 * The original layers keep their 24-second timeline; the foreground ornament orbits in 96 seconds.
 */
internal class MistBackgroundDrawable(context: Context, private val dynamicColors: Boolean = true) : Drawable(), Animatable {
    private val assets = context.assets
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val easing = PathInterpolator(0.42f, 0f, 0.58f, 1f)
    private var progress = 0f
    private var opacity = 255
    private var requested = false
    private val seed = Hct.fromInt(MaterkitColors.resolve(context).primaryColor)
    private val dark = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    // Keep the source's tonal hierarchy, using the system primary hue and chroma budget.
    private fun color(source: String): Int {
        val original = Color.parseColor(source)
        if (!dynamicColors) return original
        val hct = Hct.fromInt(original)
        return Hct.from(seed.hue, min(hct.chroma, seed.chroma),
            if (dark) 100.0 - hct.tone else hct.tone).toInt()
    }

    private val backdrop = color("#F0F5EF")
    private val ornamentColor = color("#81BD9E")
    // In Quiet foreground's local coordinates: same center (1000, 880), inset from its edge.
    // PathMeasure gives constant distance per second instead of accelerating at ellipse ends.
    private val orbit = PathMeasure(Path().apply {
        addOval(260f, 90f, 1740f, 1670f, Path.Direction.CW)
    }, true)
    private val orbitPosition = FloatArray(2)
    private val ornament = Path().apply {
        // Eight soft lobes, diameter 164 master pixels; geometry is cached for the drawable's life.
        val count = 96
        val points = Array(count) { i ->
            val angle = 2.0 * PI * i / count
            val radius = 76.0 + 6.0 * cos(8.0 * angle)
            floatArrayOf((radius * cos(angle)).toFloat(), (radius * sin(angle)).toFloat())
        }
        moveTo((points.last()[0] + points[0][0]) / 2f, (points.last()[1] + points[0][1]) / 2f)
        for (i in points.indices) {
            val next = points[(i + 1) % count]
            quadTo(points[i][0], points[i][1], (points[i][0] + next[0]) / 2f, (points[i][1] + next[1]) / 2f)
        }
        close()
    }
    private data class Layer(val path: Path, val x: Float, val y: Float,
        val width: Float, val height: Float, val color: Int, val alpha: Int,
        val dx: FloatArray, val dy: FloatArray, val scales: FloatArray)

    private fun ellipse(asset: String, x: Float, y: Float,
        dx: FloatArray, dy: FloatArray, scales: FloatArray): Layer {
        val parser = Xml.newPullParser()
        var width = 0f; var height = 0f
        var fill = backdrop; var alpha = 255
        val path = Path()
        assets.open("background/mist/$asset.svg").use { input ->
            parser.setInput(input, "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG) continue
                fun attr(name: String) = parser.getAttributeValue(null, name)
                when (parser.name) {
                    "svg" -> { width = attr("width").toFloat(); height = attr("height").toFloat() }
                    "ellipse" -> {
                        val cx = attr("cx").toFloat(); val cy = attr("cy").toFloat()
                        val rx = attr("rx").toFloat(); val ry = attr("ry").toFloat()
                        path.addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW)
                        fill = color(attr("fill"))
                        alpha = ((attr("opacity")?.toFloat() ?: 1f) * 255f).toInt()
                    }
                }
            }
        }
        require(width > 0 && height > 0 && !path.isEmpty) { "Invalid Figma asset: $asset" }
        return Layer(path, x, y, width, height, fill, alpha, dx, dy, scales)
    }

    private val layers = listOf(
        ellipse("canopy", 360f, -600f, floatArrayOf(0f,-14f,8f,16f,0f), floatArrayOf(0f,10f,16f,-8f,0f), floatArrayOf(1f,1.008f,1.012f,1.006f,1f)),
        ellipse("shoulder", 790f, -320f, floatArrayOf(0f,-8f,6f,9f,0f), floatArrayOf(0f,6f,10f,-5f,0f), floatArrayOf(1f,1.004f,1.008f,1.004f,1f)),
        ellipse("side", 850f, -250f, floatArrayOf(0f,-12f,12f,18f,0f), floatArrayOf(0f,8f,-10f,6f,0f), floatArrayOf(1f,1.006f,1.012f,1.006f,1f)),
        ellipse("sweep", -420f, 640f, floatArrayOf(0f,12f,-8f,-16f,0f), floatArrayOf(0f,-14f,-22f,-8f,0f), floatArrayOf(1f,1.004f,1.008f,1.004f,1f)),
        ellipse("returning", -540f, 820f, floatArrayOf(0f,7f,-5f,-9f,0f), floatArrayOf(0f,-8f,-13f,-5f,0f), floatArrayOf(1f,1.003f,1.005f,1.003f,1f)),
        ellipse("tide", 600f, 1010f, floatArrayOf(0f,-12f,-18f,6f,0f), floatArrayOf(0f,-6f,8f,12f,0f), floatArrayOf(1f,1.006f,1.01f,1.006f,1f)),
        ellipse("foreground", -1050f, -320f, floatArrayOf(0f,12f,18f,-6f,0f), floatArrayOf(0f,4f,-8f,-12f,0f), floatArrayOf(1f,1.005f,1.009f,1.004f,1f)),
        Layer(Path().apply { addRoundRect(0f,0f,1040f,650f,
            floatArrayOf(180f,180f,325f,325f,325f,325f,100f,100f), Path.Direction.CW) },
            -320f,235f,1040f,650f,color("#DEE8E0"),178,
            floatArrayOf(0f,7f,10f,-4f,0f), floatArrayOf(0f,2f,-5f,-7f,0f), floatArrayOf(1f,1.003f,1.005f,1.003f,1f)),
    )
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 96_000L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener { progress = it.animatedValue as Float; invalidateSelf() }
    }

    override fun draw(canvas: Canvas) {
        if (bounds.isEmpty || opacity == 0) return
        val save = canvas.save()
        canvas.clipRect(bounds)
        if (opacity < 255) canvas.saveLayerAlpha(bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(), opacity)
        val scale = max(bounds.width() / 1600f, bounds.height() / 1200f)
        canvas.translate(bounds.exactCenterX() - 800f * scale, bounds.exactCenterY() - 600f * scale)
        canvas.scale(scale, scale)
        paint.color = backdrop
        canvas.drawRect(0f, 0f, 1600f, 1200f, paint)
        val position = ((progress.coerceIn(0f, 1f) * 4f) % 1f) * 4f
        val segment = min(position.toInt(), 3)
        val fraction = easing.getInterpolation(position - segment)
        fun sample(values: FloatArray) = values[segment] + (values[segment + 1] - values[segment]) * fraction
        for (layer in layers) {
            val layerSave = canvas.save()
            canvas.translate(layer.x + sample(layer.dx), layer.y + sample(layer.dy))
            val s = sample(layer.scales)
            canvas.scale(s, s, layer.width / 2f, layer.height / 2f)
            paint.color = layer.color
            paint.alpha = layer.alpha
            canvas.drawPath(layer.path, paint)
            canvas.restoreToCount(layerSave)
        }
        // Draw above the shoulder, using the foreground's exact animated transform.
        val foreground = layers[6]
        canvas.translate(foreground.x + sample(foreground.dx), foreground.y + sample(foreground.dy))
        val foregroundScale = sample(foreground.scales)
        canvas.scale(foregroundScale, foregroundScale, foreground.width / 2f, foreground.height / 2f)
        orbit.getPosTan((progress % 1f) * orbit.length, orbitPosition, null)
        canvas.translate(orbitPosition[0], orbitPosition[1])
        // Rotate about the ornament's own center, sharing the orbit clock (24 seconds per turn).
        canvas.rotate(progress * 1440f)
        paint.color = ornamentColor
        canvas.drawPath(ornament, paint)
        canvas.restoreToCount(save)
    }

    override fun start() { requested = true; updatePlayback() }
    override fun stop() { requested = false; updatePlayback() }
    override fun isRunning() = animator.isRunning && !animator.isPaused
    override fun setVisible(visible: Boolean, restart: Boolean): Boolean {
        val changed = super.setVisible(visible, restart)
        if (restart) { animator.cancel(); progress = 0f; invalidateSelf() }
        updatePlayback()
        return changed
    }
    private fun updatePlayback() {
        if (requested && isVisible && ValueAnimator.areAnimatorsEnabled()) {
            if (animator.isPaused) animator.resume() else if (!animator.isStarted) animator.start()
        } else if (animator.isStarted && !animator.isPaused) animator.pause()
    }
    fun release() { requested = false; animator.cancel() }
    override fun setAlpha(alpha: Int) { opacity = alpha.coerceIn(0,255); invalidateSelf() }
    override fun getAlpha() = opacity
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
