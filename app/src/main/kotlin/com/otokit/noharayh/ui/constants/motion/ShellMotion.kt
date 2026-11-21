package com.otokit.noharayh.ui.constants.motion

/**
 * 动画曲线的纯标识。
 *
 * 只登记名称，不实例化 Interpolator；执行器在使用时创建运行对象。
 */
enum class MotionCurve { FastOutSlowIn, OutQuint, Standard, Emphasized, GentleBounce }

enum class PageMotionStyle(val key: String, val curve: MotionCurve) {
    Standard("standard", MotionCurve.Standard),
    Emphasized("emphasized", MotionCurve.Emphasized),
    GentleBounce("gentle_bounce", MotionCurve.GentleBounce);

    companion object {
        fun restore(key: String?) = entries.firstOrNull { it.key == key } ?: Standard
    }
}

/**
 * 转场效果的纯描述。
 *
 * 只描述允许变化哪类属性，不含时长、View 或 Interpolator；
 * 具体幅度、位移量由各组配置提供，由 animation 层统一解释。
 */
enum class TransitionKind {
    Fade,
    Slide,
    Scale,
    ScaleFadeSlide,
}

/** 标准按压反馈。 */
object PressFeedback {
    const val SCALE: Float = 0.95f
    const val PRESS_DURATION_MS: Long = 90L
    const val RELEASE_DURATION_MS: Long = 120L
}

/** 共用默认节奏；页面转场与外围反馈分别有明确入口。 */
object MotionDefaults {
    val PAGE_CURVE: MotionCurve = MotionCurve.OutQuint
    val CHROME_CURVE: MotionCurve = MotionCurve.FastOutSlowIn
    val EMPHASIZED_CURVE: MotionCurve = MotionCurve.FastOutSlowIn
}

object NavigationTransition {
    const val DURATION_MS = 320L
    const val DISTANCE_DP = 32f
}

/** 全程连续的卡片退场；缩放、位移、淡出共享走势，不设置静止区间。 */
object PageCardTransition {
    const val SHRINK_STEEPNESS = 6.0
    const val SHRINK_CENTER = 0.58
}

/** 手机标题的局部幅度，量纲与内容区解耦。 */
object NavTitleTransition {
    const val TRANSLATION_ROW_HEIGHT_FRACTION: Float = 0.76f
    const val TRANSLATION_MAX_DP: Float = 44f
}

/** 双页联动由同一前景进度驱动。 */
object PagerPairTransition {
    val KIND: TransitionKind = TransitionKind.Slide
    val CURVE: MotionCurve = MotionDefaults.EMPHASIZED_CURVE
    const val SWIPE_SETTLE_MS = NavigationTransition.DURATION_MS
}

object SettingsTransition {
    const val ENTER_DURATION_MS = 320L
    const val EXIT_DURATION_MS = 320L
    const val SCRIM_MAX_ALPHA = .20f
    const val DISTANCE_DP = 20f
}

/** 主题色变化（装饰三角、菜单圆底）的统一过渡。 */
object ChromeThemeTransition {
    const val DURATION_MS: Long = 260L
    val CURVE: MotionCurve = MotionDefaults.CHROME_CURVE
}

/** 菜单/返回图标的交叉淡变。 */
object ChromeIconTransition {
    val KIND: TransitionKind = TransitionKind.Fade
    /** 只在启用缩放/位移类型时使用；默认 Fade 保持原来的纯交叉淡变。 */
    const val SCALE_FROM: Float = 0.85f
    const val TRANSLATION_SIZE_FRACTION: Float = 0.25f
    const val DURATION_MS: Long = 200L
    val CURVE: MotionCurve = MotionDefaults.CHROME_CURVE
}
