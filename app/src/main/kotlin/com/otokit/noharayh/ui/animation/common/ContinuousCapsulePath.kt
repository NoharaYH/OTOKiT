/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0.
 * See assets/licenses/Capsule-LICENSE.txt or https://www.apache.org/licenses/LICENSE-2.0
 *
 * Adapted from Kyant0/Capsule (830975a0515e94444c59cf5dccaf35909a1c0f50),
 * G2Continuity.createHorizontalCapsulePathSegments and G2ContinuityProfile.createBaseBezier.
 * Changes: Android Path adapter, equal curvature scales (1.0), cached geometry, and
 * decreasing transition angle near square bounds for a continuous circle limit.
 */
package com.otokit.noharayh.ui.animation.common

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Unit-radius transition from the straight edge to a circular end. */
internal data class CapsuleTransition(
    val extension: Double,
    val control1: Double,
    val control2: Double,
    val endX: Double,
    val endY: Double,
    val angle: Double,
)

internal fun capsuleTransition(aspect: Double): CapsuleTransition {
    val maximumExtension = 0.5286651 * 0.75
    val fraction = ((aspect - 1.0) / maximumExtension).coerceIn(0.0, 1.0)
    val angle = PI / 4.0 * fraction
    val sine = sin(angle)
    val cosine = cos(angle)
    val halfTan = sine / (1.0 + cosine)
    return CapsuleTransition(
        maximumExtension * fraction,
        (1.0 - 1.5 / (1.0 + cosine)) * halfTan,
        halfTan, sine, 1.0 - cosine, angle,
    )
}

/** A capsule with G2 line/Bezier/circle joins; circle endpoints retain their circular arc. */
internal class ContinuousCapsulePath {
    private val arc = RectF()
    private val transform = Matrix()
    private var previousAspect = Double.NaN
    private var transition = capsuleTransition(1.0)

    fun setPath(path: Path, bounds: RectF) {
        path.rewind()
        if (bounds.isEmpty) return
        val vertical = bounds.height() > bounds.width()
        val length = maxOf(bounds.width(), bounds.height())
        val thickness = minOf(bounds.width(), bounds.height())
        if (length == thickness) {
            path.addOval(bounds, Path.Direction.CW)
            return
        }
        val aspect = length.toDouble() / thickness
        if (aspect != previousAspect) {
            transition = capsuleTransition(aspect)
            previousAspect = aspect
        }
        val r = thickness / 2f
        val extension = (transition.extension * r).toFloat()
        val c1 = (transition.control1 * r).toFloat()
        val c2 = (transition.control2 * r).toFloat()
        val ex = (transition.endX * r).toFloat()
        val ey = (transition.endY * r).toFloat()
        val angle = (transition.angle * 180.0 / PI).toFloat()
        val rightCenter = length - r
        path.moveTo(r + extension, 0f)
        path.lineTo(rightCenter - extension, 0f)
        path.cubicTo(rightCenter + c1, 0f, rightCenter + c2, 0f, rightCenter + ex, ey)
        arc.set(length - thickness, 0f, length, thickness)
        path.arcTo(arc, -90f + angle, 180f - 2f * angle, false)
        path.cubicTo(rightCenter + c2, thickness, rightCenter + c1, thickness, rightCenter - extension, thickness)
        path.lineTo(r + extension, thickness)
        path.cubicTo(r - c1, thickness, r - c2, thickness, r - ex, thickness - ey)
        arc.set(0f, 0f, thickness, thickness)
        path.arcTo(arc, 90f + angle, 180f - 2f * angle, false)
        path.cubicTo(r - c2, 0f, r - c1, 0f, r + extension, 0f)
        path.close()
        transform.reset()
        if (vertical) {
            transform.setRotate(90f)
            transform.postTranslate(bounds.left + thickness, bounds.top)
        } else {
            transform.setTranslate(bounds.left, bounds.top)
        }
        path.transform(transform)
    }
}
