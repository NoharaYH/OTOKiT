package com.otokit.noharayh.ui.constants.motion

/** 主题选择的固定节奏和运动参数，不持有执行对象。 */
internal object ThemeSelectionMotion {
    const val PANEL_DURATION_MS = 420L
    const val PANEL_EXTENSION_END = 0.65f
    const val PANEL_MENU_START = 0.6f
    const val PANEL_GAP_START = 0.15f
    const val CARD_DURATION_MS = 300L
    const val FIELD_TRANSLATION_DP = 8f
    const val DISABLED_BLEND = 0.62f
    const val CHOICE_DURATION_MS = 220L
    const val ARROW_DURATION_MS = 220L
    const val SPRING_DAMPING = 0.82f
    const val SPRING_STIFFNESS = 240f
    const val MIN_VISIBLE_CHANGE_DP = 0.2f
    const val FLING_FRICTION = 1.8f
    // AndroidX FlingAnimation 的指数衰减系数。
    const val FLING_DECAY = 4.2f * FLING_FRICTION
    const val FLING_HANDOFF_SPEED = 3f
    const val FLING_HANDOFF_DISTANCE = 0.6f
    const val SNAP_MAX_SPEED = 6f
    const val OVERSCROLL_DRAG_FACTOR = 0.3f
    const val OVERSCROLL_SHIFT_FACTOR = -0.25f
    const val FEEDBACK_REARM_FRACTION = 0.9f
    const val LABEL_FADE_START = 0.45f
    // 连续欠阻尼运动：初速度拉出，允许一次可见的挤压回摆，不在顶点切换弹簧。
    const val SCROLLBAR_DAMPING = 0.5f
    const val SCROLLBAR_STIFFNESS = 420f
    const val SCROLLBAR_INITIAL_VELOCITY = 24f
    const val SCROLLBAR_EXIT_FRACTION = 1f - PANEL_EXTENSION_END
}

/** 保留现有固定黑幕时序；等待 HTML 就绪的改动另行处理。 */
internal object BackgroundSwitchMotion {
    const val COVER_MS = 120L
    const val HOLD_MS = 260L
    const val REVEAL_MS = 300L
    const val FINISH_DELAY_MS = 340L
}

/** 背景跟色开关仅过渡纯色，不进入背景来源切换。 */
internal object NoneBackgroundColorMotion {
    const val DURATION_MS = 300L
    val CURVE = MotionCurve.FastOutSlowIn
}
