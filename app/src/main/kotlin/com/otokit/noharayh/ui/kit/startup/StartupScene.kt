package com.otokit.noharayh.ui.kit.startup

import android.content.res.Resources
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.view.animation.PathInterpolator
import androidx.core.graphics.PathParser
import com.otokit.noharayh.R
import com.otokit.noharayh.ui.animation.startup.StartupTrack
import org.xmlpull.v1.XmlPullParser

/** 原始 XML 矢量与时间轴只解析一次；不保存 Context、View 或进程级可变缓存。 */
internal class StartupScene private constructor(val durationMs: Long, val nodes: List<Node>) {
    class Node(val type: String, val attributes: Map<String, String>) {
        val paths = mutableListOf<Ink>()
        val children = mutableListOf<Node>()
        val tracks = mutableMapOf<String, StartupTrack>()
        fun attr(name: String) = attributes.getValue(name).toFloat()
        fun value(name: String, time: Float, default: Float = 0f) =
            tracks[name]?.value(time)?.toFloat() ?: default
    }
    data class Ink(val path: Path, val color: Int, val stroke: Float)

    companion object {
        fun load(resources: Resources): StartupScene {
            resources.getXml(R.xml.startup_scene).use { xml ->
                val nodes = mutableListOf<Node>()
                val stack = ArrayDeque<Node>()
                var duration = 0L
                var property = ""
                var keys = mutableListOf<StartupTrack.Key>()
                while (xml.eventType != XmlPullParser.END_DOCUMENT) {
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    if (xml.eventType == XmlPullParser.START_TAG) when (xml.name) {
                        "scene" -> duration = xml.getAttributeValue(null, "duration").toLong()
                        "decoration", "arrows", "frame", "ring", "kit" -> {
                            val node = Node(xml.name, (0 until xml.attributeCount).associate {
                                xml.getAttributeName(it) to xml.getAttributeValue(it)
                            })
                            if (stack.isEmpty()) nodes.add(node) else stack.last().children.add(node)
                            stack.addLast(node)
                        }
                        "path" -> {
                            val path = requireNotNull(PathParser.createPathFromPathData(xml.getAttributeValue(null, "data")))
                            xml.getAttributeValue(null, "matrix")?.let { encoded ->
                                val values = encoded.split(',').map(String::toFloat)
                                path.transform(Matrix().apply { setValues(floatArrayOf(
                                    values[0], values[1], values[2], values[3], values[4], values[5], 0f, 0f, 1f)) })
                            }
                            stack.last().paths.add(Ink(path, Color.parseColor(xml.getAttributeValue(null, "color")),
                                xml.getAttributeValue(null, "stroke").toFloat()))
                        }
                        "track" -> { property = xml.getAttributeValue(null, "property"); keys = mutableListOf() }
                        "key" -> {
                            val raw = xml.getAttributeValue(null, "value")
                            val easing = xml.getAttributeValue(null, "easing")
                            val bezier = if (easing.startsWith("cubic-bezier")) {
                                val p = easing.substringAfter('(').substringBefore(')').split(',').map { it.trim().toFloat() }
                                PathInterpolator(p[0], p[1], p[2], p[3])
                            } else null
                            keys.add(StartupTrack.Key(xml.getAttributeValue(null, "time").toFloat(),
                                if (raw.startsWith('#')) Color.parseColor(expandColor(raw)).toDouble() else raw.toDouble(),
                                easing == "step-end", bezier))
                        }
                    }
                    if (xml.eventType == XmlPullParser.END_TAG) when (xml.name) {
                        "track" -> stack.last().tracks[property] = StartupTrack(keys, property == "border-color")
                        "decoration", "arrows", "frame", "ring", "kit" -> stack.removeLast()
                    }
                    xml.next()
                }
                require(duration > 0 && nodes.size == 8)
                return StartupScene(duration, nodes)
            }
        }

        private fun expandColor(value: String) = if (value.length == 4)
            "#" + value.drop(1).flatMap { listOf(it, it) }.joinToString("") else value
    }
}
