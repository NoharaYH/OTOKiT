package com.otokit.noharayh.ui.constants.dimensions

/** Shared G2 profile. Dimensionless; component radii remain in their own Dimensions.
 * Curvature at each Bezier/circle join is fixed at 1/r, not a styling knob.
 */
internal object ContinuousCornerSpec {
    const val MAX_EXTENSION_RATIO = 0.5286651 * 0.75
    const val MAX_TRANSITION_ANGLE_DEGREES = 45.0
}
