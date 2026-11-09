package com.otokit.noharayh.ui.animation.chrome

/**
 * 标题主按钮专属的按压参数。
 *
 * **为什么单独一份、不和内容区按钮共用：**
 * 标题栏按钮和预览按钮是两套生态——标题按钮是 45dp 的小圆钮，周围要留标题行安全边距，
 * 反馈是“黑色弹性外扩 + 圆底内收”；预览按钮是内容区的大按钮，反馈是“大黑圆扩散 + 白闪”。
 * 两者的环数量、触觉节奏和弹簧手感需要各自独立调，共用一份参数会导致改一处影响另一处。
 *
 * 数值目前与 [com.otokit.noharayh.ui.kit.common.PressSurfaceSpec] 接近，是视觉对齐的结果，
 * 不是契约；两套允许各自演进。侧边栏胶囊后续使用自己的第三份参数。
 */
internal object TitleButtonPressSpec {
    /** halo 整层合成一次的透明度（0..255）。 */
    const val HALO_ALPHA = 56

    /** 圆底半径内收量：按下时缩 3dp。 */
    const val INSET_DP = 3f

    /** 同时存在的 halo 环上限，超出时丢弃最旧的。 */
    const val MAX_RINGS = 8

    const val EXPAND_DURATION_MS = 120L

    /** 按下与点击释放两次触觉的最小间隔，避免快速点击被系统忽略。 */
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
