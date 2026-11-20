package com.otokit.noharayh.ui.animation.common

/** Maps a nonnegative drag distance to visible travel, with all distances in the same unit. */
internal fun resistedDragDistance(distance: Float, threshold: Float, maxOvershoot: Float): Float {
    if (distance <= threshold) return distance
    val extra = distance - threshold
    return threshold + maxOvershoot * extra / (maxOvershoot + extra)
}
