/*
 * Copyright 2025 Kyant
 * Licensed under Apache-2.0; see assets/licenses/Capsule-LICENSE.txt.
 * Adapted from Kyant0/Capsule's G2Continuity/G2ContinuityProfile via capsuleTransition.
 * Modified for Android Path: independent horizontal/vertical transition room, equal
 * curvature scales, and continuously vanishing transitions at capsule/circle limits.
 */
package com.otokit.noharayh.ui.animation.common

import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI

/** G2 corners that become capsule ends when the vertical straight edge disappears. */
internal class ContinuousRoundedPath {
    private val arc = RectF()

    fun horizontalExtent(radius: Float, bounds: RectF): Float = extent(radius, bounds, true)
    fun verticalExtent(radius: Float, bounds: RectF): Float = extent(radius, bounds, false)

    private fun boundedRadius(radius: Float, bounds: RectF) =
        radius.coerceIn(0f, (minOf(bounds.width(), bounds.height()) / 2f).coerceAtLeast(0f))

    private fun extent(radius: Float, bounds: RectF, horizontal: Boolean): Float {
        val r = boundedRadius(radius, bounds)
        if (r == 0f) return 0f
        val room = (if (horizontal) bounds.width() else bounds.height()) / 2f
        return (r * (1.0 + capsuleTransition(room.toDouble() / r).extension)).toFloat()
    }

    /** Append one clockwise quarter: top-right, bottom-right, bottom-left, top-left. */
    fun corner(path: Path, bounds: RectF, radius: Float, index: Int) {
        val r = boundedRadius(radius, bounds)
        val x = if (index < 2) bounds.right else bounds.left
        val y = if (index == 0 || index == 3) bounds.top else bounds.bottom
        if (r == 0f) { path.lineTo(x, y); return }
        val h = capsuleTransition(bounds.width().toDouble() / (2.0 * r))
        val v = capsuleTransition(bounds.height().toDouble() / (2.0 * r))
        val first = if (index % 2 == 0) h else v
        val second = if (index % 2 == 0) v else h
        fun px(u: Double, w: Double): Float = x + (r * when (index) {
            0 -> u; 1 -> -w; 2 -> -u; else -> w
        }).toFloat()
        fun py(u: Double, w: Double): Float = y + (r * when (index) {
            0 -> w; 1 -> u; 2 -> -w; else -> -u
        }).toFloat()
        if (first.angle > 0.0) path.cubicTo(
            px(-1 + first.control1, 0.0), py(-1 + first.control1, 0.0),
            px(-1 + first.control2, 0.0), py(-1 + first.control2, 0.0),
            px(-1 + first.endX, first.endY), py(-1 + first.endX, first.endY),
        )
        val centerX = px(-1.0, 1.0)
        val centerY = py(-1.0, 1.0)
        arc.set(centerX - r, centerY - r, centerX + r, centerY + r)
        val start = (-90.0 + index * 90.0 + first.angle * 180.0 / PI).toFloat()
        val sweep = (90.0 - (first.angle + second.angle) * 180.0 / PI).coerceAtLeast(0.0).toFloat()
        if (sweep > 0f) path.arcTo(arc, start, sweep, false)
        if (second.angle > 0.0) path.cubicTo(
            px(0.0, 1 - second.control2), py(0.0, 1 - second.control2),
            px(0.0, 1 - second.control1), py(0.0, 1 - second.control1),
            px(0.0, 1 + second.extension), py(0.0, 1 + second.extension),
        )
    }

    fun setPath(path: Path, bounds: RectF, top: Float, bottom: Float = top) {
        path.rewind()
        if (bounds.isEmpty) return
        path.moveTo(bounds.left + horizontalExtent(top, bounds), bounds.top)
        path.lineTo(bounds.right - horizontalExtent(top, bounds), bounds.top)
        corner(path, bounds, top, 0)
        path.lineTo(bounds.right, bounds.bottom - verticalExtent(bottom, bounds))
        corner(path, bounds, bottom, 1)
        path.lineTo(bounds.left + horizontalExtent(bottom, bounds), bounds.bottom)
        corner(path, bounds, bottom, 2)
        path.lineTo(bounds.left, bounds.top + verticalExtent(top, bounds))
        corner(path, bounds, top, 3)
        path.close()
    }
}
