package com.otokit.noharayh.ui.animation.common

import android.graphics.Path
import android.graphics.RectF

/** Shared continuous-corner approximation. Full-radius circles/capsules keep circular ends.
 * Two cubic segments per corner meet with matching tangent and curvature; at full smoothing
 * the straight-edge joins have zero curvature. This is not Apple's private curve implementation.
 */
internal object ContinuousCorners {
    fun smoothing(radius: Float, limit: Float): Float =
        if (radius <= 0f) 0f else ((limit / radius - 1f) * 2f).coerceIn(0f, 1f)

    fun extent(radius: Float, limit: Float): Float {
        val r = radius.coerceIn(0f, limit.coerceAtLeast(0f))
        return r * (1f + 0.5f * smoothing(r, limit))
    }

    /** Corner index: top-right, bottom-right, bottom-left, top-left; clockwise. */
    fun corner(path: Path, x: Float, y: Float, radius: Float, limit: Float, index: Int) {
        val r = radius.coerceIn(0f, limit.coerceAtLeast(0f))
        val s = smoothing(r, limit)
        val e = extent(r, limit)
        val a = 0.7347835f + (0.76f - 0.7347835f) * s
        val b = 0.1053568f * (1f - s)
        val c = 0.4804296f + (0.38f - 0.4804296f) * s
        val d = 0.2928932f + (0.19f - 0.2928932f) * s
        fun px(u: Float, v: Float) = x + e * when (index) {
            0 -> -v; 1 -> -u; 2 -> v; else -> u
        }
        fun py(u: Float, v: Float) = y + e * when (index) {
            0 -> u; 1 -> -v; 2 -> -u; else -> v
        }
        path.cubicTo(px(0f, a), py(0f, a), px(b, c), py(b, c), px(d, d), py(d, d))
        path.cubicTo(px(c, b), py(c, b), px(a, 0f), py(a, 0f), px(1f, 0f), py(1f, 0f))
    }

    fun setPath(path: Path, rect: RectF, top: Float, bottom: Float = top,
        topLimit: Float = minOf(rect.width(), rect.height()) / 2f,
    ) = setCorners(path, rect, top, top, bottom, bottom, topLimit)

    fun setCorners(path: Path, rect: RectF, topLeft: Float, topRight: Float,
        bottomRight: Float, bottomLeft: Float,
        topLimit: Float = minOf(rect.width(), rect.height()) / 2f,
    ) {
        path.rewind()
        if (rect.isEmpty) return
        val limit = minOf(rect.width(), rect.height()) / 2f
        val tl = topLimit.coerceIn(0f, limit)
        path.moveTo(rect.left + extent(topLeft, tl), rect.top)
        path.lineTo(rect.right - extent(topRight, tl), rect.top)
        corner(path, rect.right, rect.top, topRight, tl, 0)
        path.lineTo(rect.right, rect.bottom - extent(bottomRight, limit))
        corner(path, rect.right, rect.bottom, bottomRight, limit, 1)
        path.lineTo(rect.left + extent(bottomLeft, limit), rect.bottom)
        corner(path, rect.left, rect.bottom, bottomLeft, limit, 2)
        path.lineTo(rect.left, rect.top + extent(topLeft, tl))
        corner(path, rect.left, rect.top, topLeft, tl, 3)
        path.close()
    }
}
