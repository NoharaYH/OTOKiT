package com.otokit.noharayh.ui.kit.common

/**
 * 通用按压容器 [PressSurface] 的默认参数。
 *
 * 这是“内容区按钮”这一生态的参数表：大黑圆扩散、白闪覆盖、可配置内收。
 * 标题栏的圆钮有自己的 [com.otokit.noharayh.ui.animation.chrome.TitleButtonPressSpec]，
 * 侧边栏胶囊后续也会有自己的参数——三套互不共享。
 *
 * **为什么允许出现看起来重复的数值：** 不同按钮的尺寸、留白、遮罩关系不同，
 * 视觉上暂时对齐不等于应该共用一份参数；共用会让改一个按钮时误伤另一个。
 * 需要统一调整时，分别改各自的 Spec，而不是抽回公共对象。
 */
internal object PressSurfaceSpec {
    const val WHITE_ALPHA = 0.16f
    const val BLACK_ALPHA = 56

    /** 形状每边内收量：按下时半径缩 3dp。 */
    const val INSET_DP = 3f

    const val MAX_RIPPLES = 8

    const val EXPAND_DURATION_MS = 120L

    /** 按下与点击释放两次触觉的最小间隔。 */
    const val MIN_HAPTIC_GAP_MS = 120L

    /** 需要离开当前窗口的操作，给已受理的抬起反馈留出播放时间。 */
    const val RELEASE_FEEDBACK_SETTLE_MS = 60L

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
