package com.otokit.noharayh.ui.constants.motion

/**
 * 动画曲线的纯标识。
 *
 * 本批只登记名称，不实例化 Interpolator；执行器在 09 首次使用时再创建运行对象。
 */
enum class MotionCurve { FastOutSlowIn }

/** 标准按压反馈。 */
object PressFeedback {
    const val SCALE: Float = 0.95f
    const val PRESS_DURATION_MS: Long = 90L
    const val RELEASE_DURATION_MS: Long = 120L
}

/** 导航切换功能页时内容区的缩放与纵向位移。 */
object NavContentTransition {
    const val DURATION_MS: Long = 260L
    const val SCALE: Float = 0.85f

    /** 纵向位移占安全内容高度的比例。 */
    const val TRANSLATION_HEIGHT_FRACTION: Float = 0.4f

    val CURVE: MotionCurve = MotionCurve.FastOutSlowIn
}

/** 手机功能页标题列的纵向位移，量纲与内容区解耦。 */
object NavTitleTransition {
    const val TRANSLATION_ROW_HEIGHT_FRACTION: Float = 0.38f
    const val TRANSLATION_MAX_DP: Float = 22f
}

/** 双页联动的横移、缩放与透明度。 */
object PagerPairTransition {
    const val DURATION_MS: Long = 260L

    /** 横移距离占容器宽度的比例。 */
    const val TRANSLATION_RATIO: Float = 0.7f
    const val MIN_SCALE: Float = 0.7f
    const val MIN_ALPHA: Float = 0f

    val CURVE: MotionCurve = MotionCurve.FastOutSlowIn
}

/** 进出设置时的整体过渡。 */
object SettingsTransition {
    const val DURATION_MS: Long = 300L

    /** 功能页缩小到的比例。 */
    const val CONTENT_SCALE: Float = 0.80f

    /** 覆盖层入场起始放大比例。 */
    const val OVERLAY_SCALE_FROM: Float = 1.10f

    val CURVE: MotionCurve = MotionCurve.FastOutSlowIn
}

/** 设置标题在功能态与设置态之间的缩放量纲。 */
object SettingsTitleTransition {
    const val SCALE_SHRINK: Float = 0.7f
    const val SCALE_EXPAND_FROM: Float = 1.08f
}
