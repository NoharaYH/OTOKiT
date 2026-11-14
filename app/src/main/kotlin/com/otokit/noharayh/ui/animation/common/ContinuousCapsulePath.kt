package com.otokit.noharayh.ui.animation.common

import android.graphics.Path
import android.graphics.RectF

/** Capsule adapter over the shared corner implementation. */
internal class ContinuousCapsulePath {
    private val shape = ContinuousRoundedPath()
    fun setPath(path: Path, bounds: RectF) =
        shape.setPath(path, bounds, minOf(bounds.width(), bounds.height()).coerceAtLeast(0f) / 2f)
}
