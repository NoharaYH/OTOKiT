package com.otokit.noharayh.ui.kit.settings

import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Xml
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withTranslation
import androidx.core.graphics.withClip
import com.materialkolor.hct.Hct
import com.otokit.noharayh.ui.animation.common.ContinuousRoundedPath
import com.otokit.noharayh.ui.animation.settings.ThemePreviewFrames
import com.otokit.noharayh.ui.constants.motion.ThemePreviewMotion as Spec
import com.otokit.noharayh.ui.constants.dimensions.ThemeSelectionDimensions as Dimensions
import com.otokit.noharayh.ui.kit.common.softEightLobePath
import org.xmlpull.v1.XmlPullParser
import kotlin.math.max
import kotlin.math.min

/** 原生雾色场景的三组静态路径与第四层星星；只消费菜单已有短时钟，不启动背景长动画。 */
internal class MaterkitMenuPreview(assets: AssetManager, primary: Int) {
    private data class Shape(val path: Path, val source: Int, val alpha: Int = 255)

    private fun ellipse(assets: AssetManager, name: String, x: Float, y: Float): Shape {
        val parser = Xml.newPullParser()
        assets.open("background/mist/$name.svg").use { input ->
            parser.setInput(input, "UTF-8")
            while (parser.next() != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType != XmlPullParser.START_TAG || parser.name != "ellipse") continue
                fun attr(name: String) = parser.getAttributeValue(null, name)
                val cx = attr("cx").toFloat() + x
                val cy = attr("cy").toFloat() + y
                val rx = attr("rx").toFloat()
                val ry = attr("ry").toFloat()
                return Shape(Path().apply { addOval(cx - rx, cy - ry, cx + rx, cy + ry, Path.Direction.CW) },
                    attr("fill").toColorInt(), ((attr("opacity")?.toFloat() ?: 1f) * 255).toInt())
            }
        }
        error("Missing Materkit ellipse: $name")
    }

    // 直接读取现有 SVG，使用原生背景的 1600×1200 母版坐标及绘制次序。
    private val groups = listOf(
        listOf(ellipse(assets, "canopy", 360f, -600f), ellipse(assets, "shoulder", 790f, -320f),
            ellipse(assets, "side", 850f, -250f)),
        listOf(ellipse(assets, "sweep", -420f, 640f), ellipse(assets, "returning", -540f, 820f),
            ellipse(assets, "tide", 600f, 1010f)),
        listOf(ellipse(assets, "foreground", -1050f, -320f), Shape(Path().apply {
            ContinuousRoundedPath().setCorners(this, RectF(-320f, 235f, 720f, 885f), 180f, 325f, 325f, 100f)
        }, 0xFFDEE8E0.toInt(), 178)),
    )
    private val star = softEightLobePath(1f)
    private var primary = primary
    private var colors = emptyList<List<Int>>()
    private var backdrop = 0
    private var starColor = 0

    init { updateColors(primary) }

    private fun updateColors(primary: Int) {
        val seed = Hct.fromInt(primary)
        fun color(source: Int): Int {
            val value = Hct.fromInt(source)
            return Hct.from(seed.hue, min(value.chroma, seed.chroma), value.tone).toInt()
        }
        colors = groups.map { group -> group.map { color(it.source) } }
        backdrop = color(0xFFF0F5EF.toInt())
        starColor = color(0xFF81BD9E.toInt())
    }

    fun setPrimaryColor(value: Int) {
        if (value == primary) return
        primary = value
        updateColors(value)
    }

    fun draw(canvas: Canvas, bounds: RectF, fullHeight: Float, progress: Float, opacity: Float, paint: Paint) {
        val travel = themePreviewTravel(bounds.width(), fullHeight)
        val base = ThemePreviewFrames.layer(progress, 0f, Spec.BACKGROUND_END)
        paint.color = backdrop
        paint.alpha = (255 * opacity * base).toInt()
        canvas.drawRect(bounds, paint)
        val scale = max((bounds.width() + travel * 2f) / 1600f, fullHeight / 160f)
        for (group in groups.indices) {
            val phase = ThemePreviewFrames.layer(progress, group * Spec.MATERKIT_LAYER_START_STEP, 1f)
            val dx = travel * (1f - phase) * if (group == 1) 1f else -1f
            canvas.withTranslation(bounds.centerX() - 800f * scale + dx, bounds.centerY() - 600f * scale) {
                scale(scale, scale)
                for (index in groups[group].indices) {
                    val shape = groups[group][index]
                    paint.color = colors[group][index]
                    paint.alpha = (shape.alpha * opacity * phase).toInt()
                    drawPath(shape.path, paint)
                }
            }
        }
        // 星星只在左半侧短距切入；完整高度为行高两倍，仅露正中的横向一半。
        val starPhase = ThemePreviewFrames.layer(progress, Spec.MATERKIT_STAR_START, 1f)
        val radius = fullHeight * Dimensions.PREVIEW_MATERKIT_STAR_HEIGHT_FACTOR / 2f
        val starTravel = min(travel, fullHeight * Spec.MATERKIT_STAR_TRAVEL_HEIGHT_FRACTION)
        val centerX = bounds.left + min(bounds.width() * 0.25f, fullHeight)
        canvas.withClip(bounds.left, bounds.top, bounds.centerX(), bounds.bottom) {
            withTranslation(centerX - starTravel * (1f - starPhase), bounds.centerY()) {
                // 平移和顺时针转角共用同一个缓动采样，角速度随切入速度一起减至零。
                rotate(-Spec.MATERKIT_STAR_TURN_DEGREES * (1f - starPhase))
                scale(radius, radius)
                paint.color = starColor
                paint.alpha = (255 * opacity * starPhase).toInt()
                drawPath(star, paint)
            }
        }
    }
}
