package com.otokit.noharayh.ui.chrome

/**
 * 抽屉胶囊专属的按压参数（第三套生态）。
 *
 * 复用汉堡键的“内收 + 外环”思路，但状态与参数完全独立：
 * 胶囊是药丸形，外环是同形状外扩；标题键是圆形，外环是圆。
 * 数值接近是视觉对齐，不是契约，两边允许各自演进。
 */
internal object NavCapsulePressSpec {
    /** 外环颜色：黑色 80/255 透明度。 */
    const val HALO_ALPHA = 80

    /** 胶囊四边向内收缩 3dp。 */
    const val INSET_DP = 3f

    /** 外环静止外扩 6dp，峰值 9dp。 */
    const val HALO_REST_OUTSET_DP = 6f
    const val HALO_PEAK_OUTSET_DP = 9f

    const val EXPAND_DURATION_MS = 120L
    const val MIN_HAPTIC_GAP_MS = 120L
    const val MIN_VISIBLE_CHANGE = 0.002f

    const val PRESS_DAMPING = 0.85f
    const val PRESS_STIFFNESS = 1000f

    const val EXPAND_DAMPING = 0.8f
    const val EXPAND_STIFFNESS = 1600f

    const val REST_DAMPING = 0.9f
    const val REST_STIFFNESS = 300f

    const val RELEASE_DAMPING = 1f
    const val RELEASE_STIFFNESS = 700f
}
