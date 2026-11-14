/*
 * Copyright 2025 Kyant
 * Licensed under the Apache License, Version 2.0.
 * See assets/licenses/Capsule-LICENSE.txt or https://www.apache.org/licenses/LICENSE-2.0
 *
 * Adapted from Kyant0/Capsule (830975a0515e94444c59cf5dccaf35909a1c0f50),
 * G2Continuity.createHorizontalCapsulePathSegments and G2ContinuityProfile.createBaseBezier.
 * Changes: Android Path adapter, equal curvature scales (1.0), shared geometry, and
 * decreasing transition angle near square bounds for a continuous circle limit.
 */
package com.otokit.noharayh.ui.animation.common

import com.otokit.noharayh.ui.constants.dimensions.ContinuousCornerSpec
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Unit-radius transition from the straight edge to a circular end. */
internal data class CornerTransition(
    val extension: Double,
    val control1: Double,
    val control2: Double,
    val endX: Double,
    val endY: Double,
    val angle: Double,
)

internal fun cornerTransition(aspect: Double): CornerTransition {
    val maximumExtension = ContinuousCornerSpec.MAX_EXTENSION_RATIO
    val fraction = ((aspect - 1.0) / maximumExtension).coerceIn(0.0, 1.0)
    val angle = ContinuousCornerSpec.MAX_TRANSITION_ANGLE_DEGREES * PI / 180.0 * fraction
    val sine = sin(angle)
    val cosine = cos(angle)
    val halfTan = sine / (1.0 + cosine)
    return CornerTransition(
        maximumExtension * fraction,
        (1.0 - 1.5 / (1.0 + cosine)) * halfTan,
        halfTan, sine, 1.0 - cosine, angle,
    )
}

