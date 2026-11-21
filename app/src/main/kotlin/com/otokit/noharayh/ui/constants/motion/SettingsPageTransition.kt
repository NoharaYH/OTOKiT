package com.otokit.noharayh.ui.constants.motion

/** 设置父子页共用节奏；反色窗口也是进度比例，不额外计时。 */
object SettingsPageTransition {
    val PHONE_KIND = TransitionKind.Slide
    val SPLIT_KIND = TransitionKind.ScaleFadeSlide
    val CURVE = MotionDefaults.PAGE_CURVE
    const val DURATION_MS = 300L
    const val SELECTION_WINDOW = 0.25f
    const val PAGE_SCALE = 1.05f

    // 设置页统一：一条时间轴中先让位、再展开、最后内容和标题落稳。
    const val UNFOLD_DURATION_MS = 420L
    const val UNFOLD_PAGE_DISTANCE_DP = 32f
    const val UNFOLD_TITLE_DISTANCE_DP = 18f
    const val UNFOLD_TITLE_EXIT_END = 0.25f
    const val UNFOLD_TITLE_START = 0.25f
    const val UNFOLD_TITLE_END = 0.85f

    // 系统返回手势走到末段时，视觉仍留一小段由确认后的动画完成。
    const val BACK_DRAG_RESISTANCE_START = 0.60f
    const val BACK_DRAG_RESISTANCE_OVERSHOOT = 0.60f
}
